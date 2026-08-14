package com.avafli.winrsdk.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One autocomplete row: the place to resolve and the display line. */
internal data class WINRPlaceSuggestion(
    val placeId: String,
    /** `suggestion.placePrediction.text.text` — the full formatted line. */
    val text: String,
)

/**
 * The claim-form projection of a resolved place. [state] carries the
 * `administrative_area_level_1` SHORT text ("NY") per the mapping contract —
 * use [WINRPlacesClient.usStateFullName] to expand it for the State dropdown.
 */
internal data class WINRPlaceAddress(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
)

/**
 * Google Places API (New) client for the claim address step's street-field
 * autocomplete — plain HTTPS on the SDK's existing OkHttp stack, NOT the
 * Places SDK (no new dependencies).
 *
 * Both calls degrade SILENTLY: any failure (HTTP error, bad payload, network
 * loss) yields no suggestions / no fill — autocomplete is flourish and must
 * never block hand-typing the address.
 */
internal class WINRPlacesClient(
    private val apiKey: String,
    /** Overridable for tests only. */
    private val baseUrl: String = "https://places.googleapis.com/v1",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * POST /v1/places:autocomplete for [input]. Returns at most
     * [MAX_SUGGESTIONS] predictions; empty on any failure.
     */
    suspend fun autocomplete(input: String): List<WINRPlaceSuggestion> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/places:autocomplete")
                .post(
                    json.encodeToString(JsonObject.serializer(), autocompleteRequestBody(input))
                        .toRequestBody(jsonMediaType)
                )
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-Api-Key", apiKey)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSuggestions(json.parseToJsonElement(body).jsonObject)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * GET /v1/places/{placeId} with `X-Goog-FieldMask: addressComponents`,
     * mapped to the claim form's fields. Null on any failure.
     */
    suspend fun resolveAddress(placeId: String): WINRPlaceAddress? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/places/$placeId")
                .get()
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "addressComponents")
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                mapAddressComponents(json.parseToJsonElement(body).jsonObject)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }

    companion object {
        /** The dropdown shows at most this many predictions. */
        const val MAX_SUGGESTIONS = 5

        /**
         * FIXED request shape for places:autocomplete — US-only sweepstakes, so
         * the region gate matches the form's locked Country row, and the type
         * filter keeps predictions to deliverable street addresses.
         */
        fun autocompleteRequestBody(input: String): JsonObject = buildJsonObject {
            put("input", JsonPrimitive(input))
            put("includedRegionCodes", buildJsonArray { add(JsonPrimitive("us")) })
            put(
                "includedPrimaryTypes",
                buildJsonArray {
                    add(JsonPrimitive("street_address"))
                    add(JsonPrimitive("premise"))
                    add(JsonPrimitive("subpremise"))
                }
            )
        }

        /** `suggestions[].placePrediction.{placeId, text.text}`, capped at [MAX_SUGGESTIONS]. */
        fun parseSuggestions(root: JsonObject): List<WINRPlaceSuggestion> =
            (root["suggestions"] as? JsonArray).orEmpty()
                .mapNotNull { suggestion ->
                    val prediction = (suggestion as? JsonObject)
                        ?.get("placePrediction") as? JsonObject ?: return@mapNotNull null
                    val placeId = prediction["placeId"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val text = (prediction["text"] as? JsonObject)
                        ?.get("text")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    WINRPlaceSuggestion(placeId = placeId, text = text)
                }
                .take(MAX_SUGGESTIONS)

        /**
         * Maps a place-details `addressComponents` payload to the claim form:
         * street = street_number + route, city = locality (else sublocality /
         * postal_town), state = administrative_area_level_1 SHORT text,
         * zip = postal_code. Missing components map to "" — the person can
         * always finish the field by hand.
         */
        fun mapAddressComponents(root: JsonObject): WINRPlaceAddress {
            val components = (root["addressComponents"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }

            fun component(type: String): JsonObject? = components.firstOrNull { c ->
                (c["types"] as? JsonArray)?.any { it.jsonPrimitive.contentOrNull == type } == true
            }

            fun longText(type: String): String? =
                component(type)?.get("longText")?.jsonPrimitive?.contentOrNull

            fun shortText(type: String): String? =
                component(type)?.get("shortText")?.jsonPrimitive?.contentOrNull

            val street = listOfNotNull(longText("street_number"), longText("route"))
                .joinToString(" ").trim()
            val city = longText("locality")
                ?: longText("sublocality_level_1")
                ?: longText("sublocality")
                ?: longText("postal_town")
                ?: ""
            return WINRPlaceAddress(
                street = street,
                city = city,
                state = shortText("administrative_area_level_1") ?: "",
                zip = longText("postal_code") ?: "",
            )
        }

        /**
         * Expands the mapped state SHORT text ("NY") to the full name the
         * State dropdown uses ("New York"). Unknown values pass through so a
         * surprising payload still lands somewhere hand-fixable.
         */
        fun usStateFullName(shortText: String): String =
            US_STATE_NAMES[shortText.trim().uppercase()] ?: shortText

        private val US_STATE_NAMES = mapOf(
            "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
            "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
            "DC" to "District of Columbia", "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii",
            "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
            "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine",
            "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota",
            "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska",
            "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico",
            "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
            "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island",
            "SC" to "South Carolina", "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas",
            "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington",
            "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
        )
    }
}
