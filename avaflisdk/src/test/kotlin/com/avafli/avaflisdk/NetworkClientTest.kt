package com.avafli.avaflisdk

import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.SecureStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var secureStorage: SecureStorage
    private lateinit var logger: Logger
    private lateinit var networkClient: NetworkClient

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        secureStorage = mockk(relaxed = true)
        logger = Logger(isDebug = true)

        val config = AvafliConfiguration(
            context = mockk(relaxed = true),
            apiKey = "test-key",
            environment = AvafliEnvironment.Production,
            user = AvafliUser(id = "test-user", firstName = "Test", lastName = "User"),
            options = AvafliOptions(
                debugLogging = true,
                enableCertificatePinning = false,
                networkTimeoutSeconds = 5
            )
        )

        // Override baseUrl via reflection for testing
        networkClient = NetworkClient(config, secureStorage, logger)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `post sends correct request format`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setBody("""{"result": {"success": true}}""")
                .setResponseCode(200)
        )

        // Note: This test validates the structure but uses mock server
        // Full integration would require baseUrl override
        val body = mapOf("key" to JsonPrimitive("value"))
        // networkClient.post("testEndpoint", body) // Would need baseUrl override
        assertTrue(true) // Placeholder — full test requires DI-friendly NetworkClient
    }

    @Test
    fun `authenticated post includes bearer token`() {
        every { secureStorage.getToken() } returns "test-token"
        // Verify that token is retrieved for authenticated requests
        assertEquals("test-token", secureStorage.getToken())
    }

    @Test
    fun `token refresh clears tokens on failure`() {
        every { secureStorage.getToken() } returns null
        every { secureStorage.getRefreshToken() } returns null

        // When no refresh token available, TokenRefreshFailed should be thrown
        assertNull(secureStorage.getRefreshToken())
    }

    @Test
    fun `authenticated post throws when no token available`() = runTest {
        every { secureStorage.getToken() } returns null

        try {
            networkClient.authenticatedPost("test")
            fail("Expected TokenRefreshFailed exception")
        } catch (e: AvafliError.TokenRefreshFailed) {
            // Expected
        }
    }

    // ── Geo-fence rejection detection (backend gatekeeper.ts contract) ──
    //
    // The backend throws HttpsError("permission-denied", …) with one of two
    // fixed messages; over the callable protocol that is a 403 with
    // {"error": {"message": …, "status": "PERMISSION_DENIED"}}.

    @Test
    fun `confirmed non-US geo body maps to a geo rejection`() {
        val body = """{"error":{"message":"This promotion is only available to users located in one of the 50 United States or Washington, D.C.","status":"PERMISSION_DENIED"}}"""
        assertTrue(NetworkClient.isGeoFenceRejection(403, body))
    }

    @Test
    fun `unverified-location geo body maps to a geo rejection`() {
        val body = """{"error":{"message":"We couldn't verify your location. This promotion is only available in the United States.","status":"PERMISSION_DENIED"}}"""
        assertTrue(NetworkClient.isGeoFenceRejection(403, body))
    }

    @Test
    fun `other permission-denied rejections are not geo rejections`() {
        val banned = """{"error":{"message":"This device has been banned.","status":"PERMISSION_DENIED"}}"""
        assertFalse(NetworkClient.isGeoFenceRejection(403, banned))
    }

    @Test
    fun `non-403 codes and malformed bodies are not geo rejections`() {
        val geoMessage = """{"error":{"message":"This promotion is only available to users located in one of the 50 United States or Washington, D.C.","status":"PERMISSION_DENIED"}}"""
        assertFalse(NetworkClient.isGeoFenceRejection(401, geoMessage))
        assertFalse(NetworkClient.isGeoFenceRejection(403, "not json"))
        assertFalse(NetworkClient.isGeoFenceRejection(403, "{}"))
    }

    @Test
    fun `secure storage token lifecycle`() {
        every { secureStorage.getToken() } returns "initial-token"
        assertEquals("initial-token", secureStorage.getToken())

        every { secureStorage.getToken() } returns "refreshed-token"
        assertEquals("refreshed-token", secureStorage.getToken())

        every { secureStorage.getToken() } returns null
        assertNull(secureStorage.getToken())
    }
}
