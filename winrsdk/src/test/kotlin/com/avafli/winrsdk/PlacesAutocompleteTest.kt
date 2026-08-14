package com.avafli.winrsdk

import com.avafli.winrsdk.network.NetworkClient
import com.avafli.winrsdk.network.WINRPlacesClient
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Google Places address autocomplete (2.9, `sdkConfig.placesApiKey`):
 * the FIXED Places API (New) request shapes, the addressComponents → claim
 * form mapping, silent failure degradation, and the absent-key = feature-off
 * config contract.
 */
class PlacesAutocompleteTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var places: WINRPlacesClient

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        places = WINRPlacesClient(
            apiKey = "places-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    // ── Autocomplete request contract ──

    @Test
    fun `autocomplete request body has the fixed contract shape`() {
        val body = WINRPlacesClient.autocompleteRequestBody("123 Mai")

        assertEquals("123 Mai", body["input"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("us"),
            body["includedRegionCodes"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("street_address", "premise", "subpremise"),
            body["includedPrimaryTypes"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `autocomplete posts the contract body with the api key header`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "suggestions": [
                    {
                      "placePrediction": {
                        "placeId": "place-1",
                        "text": { "text": "123 Main St, Springfield, IL, USA" }
                      }
                    }
                  ]
                }
                """
            )
        )

        val suggestions = places.autocomplete("123 Mai")

        val recorded = mockServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/places:autocomplete", recorded.path)
        assertEquals("places-key", recorded.getHeader("X-Goog-Api-Key"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
        val sent = jsonObj(recorded.body.readUtf8())
        assertEquals("123 Mai", sent["input"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("us"),
            sent["includedRegionCodes"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("street_address", "premise", "subpremise"),
            sent["includedPrimaryTypes"]?.jsonArray?.map { it.jsonPrimitive.content },
        )

        assertEquals(1, suggestions.size)
        assertEquals("place-1", suggestions[0].placeId)
        assertEquals("123 Main St, Springfield, IL, USA", suggestions[0].text)
    }

    @Test
    fun `autocomplete caps the list at five suggestions`() {
        val many = (1..8).joinToString(",") { i ->
            """{"placePrediction": {"placeId": "p$i", "text": {"text": "Suggestion $i"}}}"""
        }
        val parsed = WINRPlacesClient.parseSuggestions(jsonObj("""{"suggestions": [$many]}"""))
        assertEquals(5, parsed.size)
        assertEquals("p1", parsed.first().placeId)
        assertEquals("p5", parsed.last().placeId)
    }

    @Test
    fun `autocomplete skips malformed suggestions and tolerates an empty payload`() {
        val parsed = WINRPlacesClient.parseSuggestions(
            jsonObj(
                """
                {
                  "suggestions": [
                    {"queryPrediction": {"text": {"text": "not a place"}}},
                    {"placePrediction": {"text": {"text": "no placeId"}}},
                    {"placePrediction": {"placeId": "p-ok", "text": {"text": "1 Ok Way"}}}
                  ]
                }
                """
            )
        )
        assertEquals(listOf("p-ok"), parsed.map { it.placeId })

        assertTrue(WINRPlacesClient.parseSuggestions(jsonObj("{}")).isEmpty())
    }

    @Test
    fun `autocomplete failure degrades silently to no suggestions`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("""{"error": "denied"}"""))
        assertTrue(places.autocomplete("123 Mai").isEmpty())

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(places.autocomplete("123 Mai").isEmpty())
    }

    // ── Place details request contract ──

    @Test
    fun `resolveAddress gets the place with the addressComponents field mask`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "addressComponents": [
                    {"longText": "1600", "shortText": "1600", "types": ["street_number"]},
                    {"longText": "Amphitheatre Parkway", "shortText": "Amphitheatre Pkwy", "types": ["route"]},
                    {"longText": "Mountain View", "shortText": "Mountain View", "types": ["locality", "political"]},
                    {"longText": "California", "shortText": "CA", "types": ["administrative_area_level_1", "political"]},
                    {"longText": "94043", "shortText": "94043", "types": ["postal_code"]}
                  ]
                }
                """
            )
        )

        val address = places.resolveAddress("place-1")

        val recorded = mockServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/places/place-1", recorded.path)
        assertEquals("places-key", recorded.getHeader("X-Goog-Api-Key"))
        assertEquals("addressComponents", recorded.getHeader("X-Goog-FieldMask"))

        assertNotNull(address)
        assertEquals("1600 Amphitheatre Parkway", address!!.street)
        assertEquals("Mountain View", address.city)
        // State carries the shortText per the mapping contract.
        assertEquals("CA", address.state)
        assertEquals("94043", address.zip)
    }

    @Test
    fun `resolveAddress failure degrades silently to null`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertNull(places.resolveAddress("place-1"))
    }

    // ── addressComponents mapping ──

    @Test
    fun `mapping falls back to sublocality then postal_town for city`() {
        val sublocality = WINRPlacesClient.mapAddressComponents(
            jsonObj(
                """
                {
                  "addressComponents": [
                    {"longText": "Brooklyn", "shortText": "Brooklyn", "types": ["sublocality_level_1", "sublocality", "political"]},
                    {"longText": "New York", "shortText": "NY", "types": ["administrative_area_level_1", "political"]}
                  ]
                }
                """
            )
        )
        assertEquals("Brooklyn", sublocality.city)

        val postalTown = WINRPlacesClient.mapAddressComponents(
            jsonObj(
                """
                {
                  "addressComponents": [
                    {"longText": "Springfield", "shortText": "Springfield", "types": ["postal_town"]}
                  ]
                }
                """
            )
        )
        assertEquals("Springfield", postalTown.city)
    }

    @Test
    fun `mapping handles a missing zip without dropping the other fields`() {
        val address = WINRPlacesClient.mapAddressComponents(
            jsonObj(
                """
                {
                  "addressComponents": [
                    {"longText": "1", "shortText": "1", "types": ["street_number"]},
                    {"longText": "Analytical Way", "shortText": "Analytical Way", "types": ["route"]},
                    {"longText": "Brooklyn", "shortText": "Brooklyn", "types": ["locality"]},
                    {"longText": "New York", "shortText": "NY", "types": ["administrative_area_level_1"]}
                  ]
                }
                """
            )
        )
        assertEquals("1 Analytical Way", address.street)
        assertEquals("Brooklyn", address.city)
        assertEquals("NY", address.state)
        assertEquals("", address.zip)
    }

    @Test
    fun `mapping of an empty payload yields blank fields`() {
        val address = WINRPlacesClient.mapAddressComponents(jsonObj("{}"))
        assertEquals("", address.street)
        assertEquals("", address.city)
        assertEquals("", address.state)
        assertEquals("", address.zip)
    }

    @Test
    fun `usStateFullName expands the shortText for the State dropdown`() {
        assertEquals("New York", WINRPlacesClient.usStateFullName("NY"))
        assertEquals("District of Columbia", WINRPlacesClient.usStateFullName("DC"))
        // Unknown values pass through so the field stays hand-fixable.
        assertEquals("Ontario", WINRPlacesClient.usStateFullName("Ontario"))
    }

    // ── sdkConfig.placesApiKey contract (absent key = feature off) ──

    private fun apiWith(sdkConfigJson: String): WinrApi {
        val networkClient = mockk<NetworkClient>()
        coEvery { networkClient.post("registerDevice", any()) } returns jsonObj(
            """
            {
              "token": "t", "refreshToken": "r", "uuid": "u",
              "sdkConfig": $sdkConfigJson
            }
            """
        )
        return WinrApi(networkClient, Logger(isDebug = false))
    }

    @Test
    fun `sdkConfig parses placesApiKey alongside shareUrl`() = runTest {
        val response = apiWith(
            """{"shareUrl": "https://winr.example/app", "placesApiKey": "AIza-test"}"""
        ).registerDevice("key", "fp", "bundle", "America/New_York")

        assertEquals("AIza-test", response.sdkConfig?.placesApiKey)
        assertEquals("https://winr.example/app", response.sdkConfig?.shareUrl)
    }

    @Test
    fun `absent placesApiKey stays null so autocomplete is off`() = runTest {
        val response = apiWith("""{"shareUrl": "https://winr.example/app"}""")
            .registerDevice("key", "fp", "bundle", "America/New_York")
        assertNotNull(response.sdkConfig)
        assertNull(response.sdkConfig?.placesApiKey)
    }

    @Test
    fun `blank placesApiKey is treated as absent`() = runTest {
        val response = apiWith("""{"placesApiKey": "  "}""")
            .registerDevice("key", "fp", "bundle", "America/New_York")
        assertNull(response.sdkConfig?.placesApiKey)
    }
}
