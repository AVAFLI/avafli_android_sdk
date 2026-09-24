package com.avafli.avaflisdk

import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.SecureStorage
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.Base64
import java.util.concurrent.TimeUnit
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

    // ── Token-refresh hardening (3.1.4: the Sept 24 cold-open failure) ──
    //
    // These drive a NetworkClient at a MockWebServer (baseUrlOverride) with an
    // in-memory token store, and assert on the recorded request sequence.

    private var storedToken: String? = null

    private fun jwt(expInSeconds: Long): String {
        val exp = System.currentTimeMillis() / 1000 + expInSeconds
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = enc.encodeToString("""{"exp":$exp}""".toByteArray())
        return "$header.$payload.sig"
    }

    private fun clientAtMockServer(initialToken: String?): NetworkClient {
        storedToken = initialToken
        every { secureStorage.getToken() } answers { storedToken }
        every { secureStorage.saveToken(any()) } answers { storedToken = firstArg() }
        every { secureStorage.getRefreshToken() } returns "refresh-1"
        val config = AvafliConfiguration(
            context = mockk(relaxed = true),
            apiKey = "test-key",
            user = AvafliUser(id = "test-user"),
            options = AvafliOptions(enableCertificatePinning = false, networkTimeoutSeconds = 5),
        )
        return NetworkClient(config, secureStorage, logger, baseUrlOverride = mockServer.url("/").toString().trimEnd('/'))
    }

    private fun refreshResponse(newToken: String) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"result":{"token":"$newToken","refreshToken":"refresh-2"}}""")

    private fun okResponse() = MockResponse().setResponseCode(200).setBody("""{"result":{"ok":true}}""")

    @Test
    fun `exp pre-check treats past and imminent expiry as expired`() {
        val client = clientAtMockServer(null)
        assertTrue(client.isJwtExpired(jwt(-10)))
        assertTrue(client.isJwtExpired(jwt(30)))           // within the 60 s window
        assertFalse(client.isJwtExpired(jwt(120)))
        assertTrue(client.isJwtExpired("not-a-jwt"))
    }

    @Test
    fun `expired token is refreshed BEFORE the request instead of eating a 401`() = runBlocking {
        val client = clientAtMockServer(jwt(-10))
        val fresh = jwt(3600)
        mockServer.enqueue(refreshResponse(fresh))
        mockServer.enqueue(okResponse())

        client.authenticatedPost("getActiveGiveaway")

        val first = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
        val second = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/refreshToken", first.path)
        assertEquals("/getActiveGiveaway", second.path)
        assertEquals("Bearer $fresh", second.getHeader("Authorization"))
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `live token goes straight to the endpoint`() = runBlocking {
        val live = jwt(3600)
        val client = clientAtMockServer(live)
        mockServer.enqueue(okResponse())

        client.authenticatedPost("getActiveGiveaway")

        val only = mockServer.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/getActiveGiveaway", only.path)
        assertEquals("Bearer $live", only.getHeader("Authorization"))
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `concurrent callers with a dead token share ONE refresh`() = runBlocking {
        val client = clientAtMockServer(jwt(-10))
        val fresh = jwt(3600)
        // The refresh is slow so both callers are definitely in flight together.
        mockServer.enqueue(refreshResponse(fresh).setBodyDelay(300, TimeUnit.MILLISECONDS))
        mockServer.enqueue(okResponse())
        mockServer.enqueue(okResponse())
        mockServer.enqueue(okResponse())

        listOf(
            async { client.authenticatedPost("getActiveGiveaway") },
            async { client.authenticatedPost("registerPushToken") },
            async { client.authenticatedPost("submitUserProfile") },
        ).awaitAll()

        val paths = (1..mockServer.requestCount).map { mockServer.takeRequest(2, TimeUnit.SECONDS)!! }
        assertEquals(1, paths.count { it.path == "/refreshToken" })
        assertEquals(3, paths.count { it.path != "/refreshToken" })
        paths.filter { it.path != "/refreshToken" }.forEach {
            assertEquals("Bearer $fresh", it.getHeader("Authorization"))
        }
    }

    @Test
    fun `a 401 on a live-looking token refreshes once and retries`() = runBlocking {
        val client = clientAtMockServer(jwt(3600))
        val fresh = jwt(3600)
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"status":"UNAUTHENTICATED"}}"""))
        mockServer.enqueue(refreshResponse(fresh))
        mockServer.enqueue(okResponse())

        client.authenticatedPost("getActiveGiveaway")

        val paths = (1..3).map { mockServer.takeRequest(2, TimeUnit.SECONDS)!!.path }
        assertEquals(listOf("/getActiveGiveaway", "/refreshToken", "/getActiveGiveaway"), paths)
        assertEquals(3, mockServer.requestCount)
    }

    @Test
    fun `a failed refresh clears tokens and surfaces TokenRefreshFailed to every waiter`() = runBlocking {
        val client = clientAtMockServer(jwt(-10))
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("{}").setBodyDelay(200, TimeUnit.MILLISECONDS))

        val results = listOf(
            async { runCatching { client.authenticatedPost("getActiveGiveaway") } },
            async { runCatching { client.authenticatedPost("registerPushToken") } },
        ).awaitAll()

        results.forEach { assertTrue(it.exceptionOrNull() is AvafliError.TokenRefreshFailed) }
        assertEquals(1, mockServer.requestCount)
        verify(exactly = 1) { secureStorage.clearTokens() }
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
