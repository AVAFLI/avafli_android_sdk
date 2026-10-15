package com.avafli.avaflisdk.network

import com.avafli.avaflisdk.AvafliConfiguration
import com.avafli.avaflisdk.AvafliError
import com.avafli.avaflisdk.AvafliOptions
import com.avafli.avaflisdk.AvafliUser
import com.avafli.avaflisdk.offline.OfflineErrorClassifier
import com.avafli.avaflisdk.offline.OfflineRetryCoordinator
import com.avafli.avaflisdk.offline.OfflineStateStore
import com.avafli.avaflisdk.offline.PendingIntent
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.SecureStorage
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * RESILIENCE_SPEC (Oct 15, 2026): infrastructure refusals — 429/500/502/503/504
 * with a NON-callable body (Cloud Run's plain-text "no available instance",
 * HTML, empty) — are retried with exponential backoff + full jitter
 * (1/2/4/8 s base, 250 ms floor, `Retry-After` honoured up to 10 s). A
 * callable JSON body on the same statuses is an app error and is surfaced at
 * once; other 4xx, transport failures and the 401 refresh path are unchanged.
 *
 * The client runs against a MockWebServer with the backoff sleep replaced by a
 * recorder, so the schedule is asserted exactly and no test ever sleeps.
 */
class InfraRefusalRetryTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var secureStorage: SecureStorage
    private lateinit var logger: Logger
    private val debugLines = mutableListOf<String>()
    private val sleeps = mutableListOf<Long>()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        secureStorage = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        every { logger.debug(capture(debugLines)) } just Runs
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun client(random: Random = Random.Default): NetworkClient {
        val config = AvafliConfiguration(
            context = mockk(relaxed = true),
            apiKey = "test-key",
            user = AvafliUser(id = "test-user"),
            options = AvafliOptions(enableCertificatePinning = false, networkTimeoutSeconds = 5),
        )
        return NetworkClient(
            config,
            secureStorage,
            logger,
            baseUrlOverride = mockServer.url("/").toString().trimEnd('/'),
            backoffSleep = { sleeps += it },
            jitterRandom = random,
        )
    }

    private val retryLines get() = debugLines.filter { it.startsWith(NetworkClient.INFRA_RETRY_LOG_PREFIX) }

    private fun okResponse() = MockResponse().setResponseCode(200).setBody("""{"result":{"ok":true}}""")

    /** Cloud Run's overflow refusal, verbatim from the load test. */
    private fun cloudRunRefusal(code: Int) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "text/plain")
        .setBody("The request was aborted because there was no available instance.")

    private fun htmlRefusal(code: Int) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "text/html")
        .setBody("<html><head><title>503 Service Unavailable</title></head><body>Service Unavailable</body></html>")

    private fun callableError(code: Int, status: String, message: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":{"message":"$message","status":"$status"}}""")

    /** Each jittered delay is `random(0, base) + 250 ms`. */
    private fun assertWithinSchedule(delays: List<Long>) {
        delays.forEachIndexed { i, delayMs ->
            val base = NetworkClient.INFRA_RETRY_BASE_DELAYS_MS[i]
            assertTrue("retry ${i + 1} delay $delayMs below the 250 ms floor", delayMs >= 250L)
            assertTrue("retry ${i + 1} delay $delayMs above base $base + floor", delayMs <= base + 250L)
        }
    }

    // ── 1. 429 + plain text → retried, succeeds on attempt 3 ──

    @Test
    fun `429 with a plain-text body is retried and succeeds on attempt 3`() = runBlocking {
        mockServer.enqueue(cloudRunRefusal(429))
        mockServer.enqueue(cloudRunRefusal(429))
        mockServer.enqueue(okResponse())

        val result = client().post("getActiveGiveaway")

        assertEquals(true, result["ok"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(3, mockServer.requestCount)
        // Two refusals logged + the recovery line.
        assertEquals(3, retryLines.size)
        assertTrue(retryLines[0], retryLines[0].contains("status 429") && retryLines[0].contains("retry 1/4"))
        assertTrue(retryLines[1], retryLines[1].contains("status 429") && retryLines[1].contains("retry 2/4"))
        assertTrue(retryLines[2], retryLines[2].contains("recovered on attempt 3"))
        // Two sleeps, each inside its jitter window; ≤ 3.5 s in total.
        assertEquals(2, sleeps.size)
        assertWithinSchedule(sleeps)
        assertTrue(sleeps.sum() <= 1_000L + 2_000L + 2 * 250L)
    }

    // ── 2. 503 + HTML → same ──

    @Test
    fun `503 with an HTML body is retried and succeeds on attempt 3`() = runBlocking {
        mockServer.enqueue(htmlRefusal(503))
        mockServer.enqueue(htmlRefusal(503))
        mockServer.enqueue(okResponse())

        val result = client().post("getActiveGiveaway")

        assertEquals(true, result["ok"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(3, mockServer.requestCount)
        assertEquals(3, retryLines.size)
        assertTrue(retryLines[0].contains("status 503"))
        assertEquals(2, sleeps.size)
        assertWithinSchedule(sleeps)
        assertTrue(sleeps.sum() <= 1_000L + 2_000L + 2 * 250L)
    }

    // ── 3. 429 + callable JSON (our own rate limiter) → NOT retried ──

    @Test
    fun `429 with a callable RESOURCE_EXHAUSTED body is surfaced at once`() = runBlocking {
        mockServer.enqueue(callableError(429, "RESOURCE_EXHAUSTED", "Too many requests. Please try again later."))
        mockServer.enqueue(okResponse()) // must never be consumed

        try {
            client().post("submitEmail")
            fail("Expected ServerError")
        } catch (e: AvafliError.ServerError) {
            assertEquals(429, e.code)
            assertEquals("RESOURCE_EXHAUSTED", NetworkClient.callableError(e)?.status)
        }
        assertEquals(1, mockServer.requestCount)
        assertTrue(sleeps.isEmpty())
        assertTrue(retryLines.isEmpty())
    }

    // ── 4. 400 callable error → NOT retried ──

    @Test
    fun `400 callable error is surfaced at once`() = runBlocking {
        mockServer.enqueue(callableError(400, "INVALID_ARGUMENT", "email is required"))
        mockServer.enqueue(okResponse())

        try {
            client().post("submitEmail")
            fail("Expected ServerError")
        } catch (e: AvafliError.ServerError) {
            assertEquals(400, e.code)
        }
        assertEquals(1, mockServer.requestCount)
        assertTrue(sleeps.isEmpty())
        assertTrue(retryLines.isEmpty())
    }

    // ── 5. Exhausted after 4 retries → ServerError; offline queue stays empty ──

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `exhausted refusals surface ServerError and are never queued offline`() = runTest {
        repeat(5) { mockServer.enqueue(cloudRunRefusal(500)) }
        mockServer.enqueue(okResponse()) // a 6th attempt would consume this — it must not

        val error = try {
            client().post("claimDailyEntries")
            fail("Expected ServerError")
            throw IllegalStateException()
        } catch (e: AvafliError.ServerError) {
            e
        }

        assertEquals(500, error.code)
        assertTrue(error.message!!.contains("no available instance"))
        assertEquals(5, mockServer.requestCount)          // 1 attempt + 4 retries
        assertEquals(4, sleeps.size)
        assertWithinSchedule(sleeps)
        assertTrue(sleeps.sum() <= (1_000L + 2_000L + 4_000L + 8_000L) + 4 * 250L)
        assertEquals(4, retryLines.count { it.contains("retry ") })
        assertTrue(retryLines.last().contains("gave up after 4 retries"))

        // The offline queue's transport-only classifier refuses it: the server
        // answered, it was just busy. Mirror the production gate in Avafli.kt
        // and confirm nothing lands in the queue.
        assertFalse(OfflineErrorClassifier.isRetriable(error))
        val coordinator = OfflineRetryCoordinator(
            store = MemoryStore(),
            packageName = "com.test.app",
            scope = this,
            dayKeyProvider = { "2026-10-15" },
        )
        if (OfflineErrorClassifier.isRetriable(error)) {
            coordinator.enqueue(PendingIntent.Kind.CLAIM)
        }
        assertTrue(coordinator.pendingKinds.isEmpty())
        coordinator.shutdown()
    }

    // ── 6. Retry-After: 3 → first delay ≈ 3 s ──

    @Test
    fun `Retry-After header sets the first delay`() = runBlocking {
        mockServer.enqueue(cloudRunRefusal(429).setHeader("Retry-After", "3"))
        mockServer.enqueue(okResponse())

        client().post("getActiveGiveaway")

        assertEquals(2, mockServer.requestCount)
        assertEquals(listOf(3_000L), sleeps)
    }

    // ── Schedule + classification helpers ──

    @Test
    fun `Retry-After is clamped to the jitter floor and the 10 s cap`() {
        assertEquals(10_000L, NetworkClient.infraRetryDelayMs(0, retryAfterSeconds = 120))
        assertEquals(250L, NetworkClient.infraRetryDelayMs(0, retryAfterSeconds = 0))
        assertEquals(3_000L, NetworkClient.infraRetryDelayMs(2, retryAfterSeconds = 3))
        assertNull(NetworkClient.parseRetryAfterSeconds(null))
        assertNull(NetworkClient.parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(3L, NetworkClient.parseRetryAfterSeconds(" 3 "))
    }

    @Test
    fun `jittered delays follow the 1-2-4-8 s schedule with full jitter and a 250 ms floor`() {
        // Jitter at its minimum → exactly the floor; at its maximum → base + floor.
        val zero = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        NetworkClient.INFRA_RETRY_BASE_DELAYS_MS.forEachIndexed { i, _ ->
            assertEquals(250L, NetworkClient.infraRetryDelayMs(i, null, zero))
        }
        repeat(200) {
            NetworkClient.INFRA_RETRY_BASE_DELAYS_MS.forEachIndexed { i, base ->
                val d = NetworkClient.infraRetryDelayMs(i, null)
                assertTrue(d in 250L..(base + 250L))
            }
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), NetworkClient.INFRA_RETRY_BASE_DELAYS_MS.toList())
    }

    @Test
    fun `only 429 and 5xx with a non-callable body classify as infra refusals`() {
        val plain = "The request was aborted because there was no available instance."
        val callable = """{"error":{"message":"Too many requests","status":"RESOURCE_EXHAUSTED"}}"""
        for (code in listOf(429, 500, 502, 503, 504)) {
            assertTrue("$code plain", NetworkClient.isInfraRefusal(code, plain))
            assertTrue("$code empty", NetworkClient.isInfraRefusal(code, ""))
            assertTrue("$code html", NetworkClient.isInfraRefusal(code, "<html>busy</html>"))
            assertFalse("$code callable", NetworkClient.isInfraRefusal(code, callable))
        }
        for (code in listOf(400, 401, 403, 404, 409, 501)) {
            assertFalse("$code plain", NetworkClient.isInfraRefusal(code, plain))
            assertFalse("$code callable", NetworkClient.isInfraRefusal(code, callable))
        }
        assertTrue(NetworkClient.isCallableErrorBody(callable))
        assertFalse(NetworkClient.isCallableErrorBody("""{"error":"string, not an envelope"}"""))
        assertFalse(NetworkClient.isCallableErrorBody("""{"result":{}}"""))
        assertNotNull(NetworkClient.callableError(AvafliError.ServerError(429, callable)))
    }

    /** In-memory [OfflineStateStore] for the queue assertion in test 5. */
    private class MemoryStore : OfflineStateStore {
        private val map = mutableMapOf<String, String>()
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getString(key: String): String? = map[key]
        override fun remove(key: String) { map.remove(key) }
    }
}
