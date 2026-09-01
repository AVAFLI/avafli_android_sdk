package com.avafli.avaflisdk

import com.avafli.avaflisdk.offline.BufferingAnalyticsAdapter
import com.avafli.avaflisdk.offline.OfflineErrorClassifier
import com.avafli.avaflisdk.offline.OfflineRetryCoordinator
import com.avafli.avaflisdk.offline.OfflineStateStore
import com.avafli.avaflisdk.offline.PendingIntent
import com.avafli.avaflisdk.offline.RetryOutcome
import com.avafli.avaflisdk.services.analytics.AnalyticsAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Offline resilience (launch item 15): same-day retry queue for
 * registration/claims plus the bounded offline analytics buffer. Mirrors the
 * iOS OfflineResilienceTests.
 */

// ── Fakes ──

/** In-memory store — no SharedPreferences on the JVM. */
private class MemoryStore : OfflineStateStore {
    val map = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key) }
}

private class SpyAnalyticsAdapter : AnalyticsAdapter {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun trackEvent(name: String, params: Map<String, Any?>) {
        events.add(name to params)
    }
    override fun trackScreenView(screenName: String) {}
    override fun setUserProperty(key: String, value: String) {}
}

// ── Classifier ──

class OfflineErrorClassifierTest {

    @Test
    fun `transport errors are retriable`() {
        assertTrue(OfflineErrorClassifier.isRetriable(AvafliError.NetworkError("timeout")))
        assertTrue(
            OfflineErrorClassifier.isRetriable(
                AvafliError.NetworkError("failed", IOException("reset"))
            )
        )
        // Raw transport exceptions that escaped the wrapper.
        assertTrue(OfflineErrorClassifier.isRetriable(IOException("connection reset")))
        assertTrue(OfflineErrorClassifier.isRetriable(SocketTimeoutException("timed out")))
    }

    @Test
    fun `backend rejections are not retriable`() {
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.ServerError(400, "bad request")))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.ServerError(403, "forbidden")))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.ServerError(429, "rate limited")))
        // 5xx is still a real HTTP response — transport-only policy (iOS parity).
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.ServerError(500, "boom")))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.GeoBlocked()))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.TokenRefreshFailed()))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.ServiceUnavailable()))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.OptedOut()))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.AlreadyClaimed()))
        assertFalse(OfflineErrorClassifier.isRetriable(AvafliError.Unknown("weird")))
    }

    @Test
    fun `already claimed rejection detection matches backend dedup messages`() {
        assertTrue(Avafli.isAlreadyClaimedRejection(AvafliError.AlreadyClaimed()))
        assertTrue(
            Avafli.isAlreadyClaimedRejection(
                AvafliError.ServerError(409, "Already claimed daily entries today")
            )
        )
        assertTrue(
            Avafli.isAlreadyClaimedRejection(
                AvafliError.ServerError(
                    409,
                    "You've already entered today on another device. Come back tomorrow!"
                )
            )
        )
        assertFalse(Avafli.isAlreadyClaimedRejection(AvafliError.NetworkError("timeout")))
        assertFalse(Avafli.isAlreadyClaimedRejection(AvafliError.GeoBlocked()))
    }
}

// ── Retry coordinator ──

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineRetryCoordinatorTest {

    private val store = MemoryStore()

    private fun TestScope.makeCoordinator(
        dayKey: () -> String = { "2026-09-01" },
    ) = OfflineRetryCoordinator(
        store = store,
        packageName = "com.test.app",
        scope = this,
        dayKeyProvider = dayKey,
    )

    @Test
    fun `enqueue persists intent across coordinator instances`() = runTest {
        val first = makeCoordinator()
        first.enqueue(PendingIntent.Kind.CLAIM)
        assertEquals(listOf(PendingIntent.Kind.CLAIM), first.pendingKinds)

        // A brand-new coordinator over the same store (≈ app relaunch)
        // still sees the pending intent.
        val second = makeCoordinator()
        assertEquals(listOf(PendingIntent.Kind.CLAIM), second.pendingKinds)
        first.shutdown()
        second.shutdown()
    }

    @Test
    fun `storage key uses winr namespace with package suffix`() = runTest {
        val coordinator = makeCoordinator()
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        assertNotNull(store.map["winr_offline_pending_intents_com.test.app"])
        coordinator.shutdown()
    }

    @Test
    fun `clear removes only that kind`() = runTest {
        val coordinator = makeCoordinator()
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        coordinator.enqueue(PendingIntent.Kind.REGISTRATION)
        coordinator.clear(PendingIntent.Kind.CLAIM)
        assertEquals(listOf(PendingIntent.Kind.REGISTRATION), coordinator.pendingKinds)
        coordinator.shutdown()
    }

    @Test
    fun `re-enqueueing a kind keeps one intent`() = runTest {
        val coordinator = makeCoordinator()
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        assertEquals(1, coordinator.pendingKinds.size)
        coordinator.shutdown()
    }

    @Test
    fun `same-day guard drops intent from a previous local day`() = runTest {
        var today = "2026-09-01"
        val coordinator = makeCoordinator(dayKey = { today })
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        assertEquals(listOf(PendingIntent.Kind.CLAIM), coordinator.pendingKinds)

        // Cross local midnight — the intent must be dropped, not replayed:
        // server-authoritative day windows make a stale-day claim a NEW-day
        // claim, which is the auto-open engine's decision.
        today = "2026-09-02"
        assertTrue(coordinator.pendingKinds.isEmpty())
        // And the drop is persisted.
        assertNull(store.map["winr_offline_pending_intents_com.test.app"])
        coordinator.shutdown()
    }

    @Test
    fun `success clears the intent after one attempt`() = runTest {
        val coordinator = makeCoordinator()
        var attempts = 0
        coordinator.retryHandler = { attempts++; RetryOutcome.SUCCESS }
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        advanceUntilIdle()
        assertEquals(1, attempts)
        assertTrue(coordinator.pendingKinds.isEmpty())
        coordinator.shutdown()
    }

    @Test
    fun `permanent failure drops the intent`() = runTest {
        val coordinator = makeCoordinator()
        var attempts = 0
        coordinator.retryHandler = { attempts++; RetryOutcome.PERMANENT_FAILURE }
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        advanceUntilIdle()
        assertEquals(1, attempts)
        assertTrue(coordinator.pendingKinds.isEmpty())
        coordinator.shutdown()
    }

    @Test
    fun `retriable failure keeps the intent`() = runTest {
        val coordinator = makeCoordinator()
        coordinator.retryHandler = { RetryOutcome.RETRIABLE_FAILURE }
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        advanceUntilIdle()
        assertEquals(listOf(PendingIntent.Kind.CLAIM), coordinator.pendingKinds)
        coordinator.shutdown()
    }

    @Test
    fun `attempts are hard-capped per session across all triggers`() = runTest {
        val coordinator = makeCoordinator()
        var attempts = 0
        coordinator.retryHandler = { attempts++; RetryOutcome.RETRIABLE_FAILURE }
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        advanceUntilIdle() // full backoff schedule runs (virtual time)

        // Hammer every trigger well past the cap.
        repeat(20) {
            coordinator.noteConnectivityRegained()
            coordinator.noteForeground()
            coordinator.noteLaunch()
            advanceUntilIdle()
        }

        assertEquals(OfflineRetryCoordinator.MAX_ATTEMPTS_PER_SESSION, attempts)
        assertEquals(
            OfflineRetryCoordinator.MAX_ATTEMPTS_PER_SESSION,
            coordinator.attemptCount(PendingIntent.Kind.CLAIM),
        )
        // The intent stays persisted for the NEXT session (fresh cap).
        assertEquals(listOf(PendingIntent.Kind.CLAIM), coordinator.pendingKinds)
        coordinator.shutdown()
    }

    @Test
    fun `backoff schedule is finite and stops once the queue empties`() = runTest {
        val coordinator = makeCoordinator()
        var attempts = 0
        coordinator.retryHandler = { attempts++; RetryOutcome.SUCCESS }
        coordinator.enqueue(PendingIntent.Kind.CLAIM)
        advanceUntilIdle()
        // Success on the first backoff slot — the finite loop bails out early.
        assertEquals(1, attempts)
        assertTrue(coordinator.pendingKinds.isEmpty())
        coordinator.shutdown()
    }
}

// ── Buffering analytics adapter ──

class BufferingAnalyticsAdapterTest {

    private val store = MemoryStore()
    private val spy = SpyAnalyticsAdapter()
    private var online = true

    private fun makeAdapter(nowMs: () -> Long = System::currentTimeMillis) =
        BufferingAnalyticsAdapter(
            inner = spy,
            store = store,
            packageName = "com.test.app",
            isOnline = { online },
            nowMs = nowMs,
        )

    @Test
    fun `online events pass straight through`() {
        val adapter = makeAdapter()
        adapter.trackEvent("avafli_experience_opened", mapOf("giveaway_id" to "g1"))
        assertEquals(1, spy.events.size)
        assertEquals("avafli_experience_opened", spy.events[0].first)
        assertEquals(0, adapter.bufferedCount)
    }

    @Test
    fun `offline events are buffered not forwarded`() {
        online = false
        val adapter = makeAdapter()
        adapter.trackEvent("e1", mapOf("a" to 1))
        adapter.trackEvent("e2")
        assertEquals(0, spy.events.size)
        assertEquals(2, adapter.bufferedCount)
        assertNotNull(store.map["winr_offline_analytics_buffer_com.test.app"])
    }

    @Test
    fun `buffer persists across adapter instances`() {
        online = false
        makeAdapter().trackEvent("e1")
        // New adapter over the same store (≈ next launch).
        val secondLaunch = makeAdapter()
        assertEquals(1, secondLaunch.bufferedCount)
        online = true
        secondLaunch.flush()
        assertEquals(listOf("e1"), spy.events.map { it.first })
        assertEquals(0, secondLaunch.bufferedCount)
    }

    @Test
    fun `flush preserves order and attaches original timestamps`() {
        online = false
        var now = 1_756_600_000_000L
        val adapter = makeAdapter(nowMs = { now })
        adapter.trackEvent("first", mapOf("n" to 1))
        now += 60_000
        adapter.trackEvent("second", mapOf("n" to 2))

        online = true
        adapter.flush()

        assertEquals(listOf("first", "second"), spy.events.map { it.first })
        val firstParams = spy.events[0].second
        assertEquals(1L, firstParams["n"])
        assertEquals(1_756_600_000_000L, firstParams["original_timestamp_ms"])
        assertEquals(
            java.time.Instant.ofEpochMilli(1_756_600_000_000L).toString(),
            firstParams["original_timestamp"],
        )
        assertEquals(1_756_600_060_000L, spy.events[1].second["original_timestamp_ms"])
    }

    @Test
    fun `ring buffer drops oldest beyond capacity`() {
        online = false
        val adapter = makeAdapter()
        repeat(BufferingAnalyticsAdapter.CAPACITY + 25) { i ->
            adapter.trackEvent("e$i")
        }
        assertEquals(BufferingAnalyticsAdapter.CAPACITY, adapter.bufferedCount)

        online = true
        adapter.flush()
        assertEquals(BufferingAnalyticsAdapter.CAPACITY, spy.events.size)
        // Oldest 25 dropped; the first surviving event is e25.
        assertEquals("e25", spy.events.first().first)
        assertEquals("e${BufferingAnalyticsAdapter.CAPACITY + 24}", spy.events.last().first)
    }

    @Test
    fun `live event after reconnect flushes backlog first`() {
        online = false
        val adapter = makeAdapter()
        adapter.trackEvent("buffered")

        online = true
        adapter.trackEvent("live")

        // Order preserved: the offline backlog lands before the live event.
        assertEquals(listOf("buffered", "live"), spy.events.map { it.first })
        assertEquals(0, adapter.bufferedCount)
    }

    @Test
    fun `non-primitive params degrade to strings`() {
        online = false
        val adapter = makeAdapter()
        adapter.trackEvent("e", mapOf("obj" to listOf(1, 2, 3), "ok" to true))
        online = true
        adapter.flush()
        assertEquals(1, spy.events.size)
        assertTrue(spy.events[0].second["obj"] is String)
        assertEquals(true, spy.events[0].second["ok"])
    }

    @Test
    fun `flush with empty buffer is a no-op`() {
        val adapter = makeAdapter()
        adapter.flush()
        assertEquals(0, spy.events.size)
    }

    @Test
    fun `screen views and user properties pass through even offline`() {
        online = false
        val adapter = makeAdapter()
        adapter.trackScreenView("dashboard")
        adapter.setUserProperty("k", "v")
        // State, not events — never buffered.
        assertEquals(0, adapter.bufferedCount)
    }
}
