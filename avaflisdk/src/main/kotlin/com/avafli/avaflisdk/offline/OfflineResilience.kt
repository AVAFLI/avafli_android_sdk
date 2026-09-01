package com.avafli.avaflisdk.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.avafli.avaflisdk.AvafliError
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.services.analytics.AnalyticsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/**
 * Offline resilience (launch item 15): transient network drops must not cause
 * lost streaks or distorted DAU. Scope is deliberately SAME-DAY only — a
 * pending intent is dropped when its local calendar day ends. Cross-midnight
 * backdated replay is explicitly out of scope: the backend's day windows are
 * server-authoritative (a governed anti-fraud contract; the claim transaction
 * keys dedup + streak math off `todayDateString(userTz)` /
 * `current_entry_date`), so a client replaying yesterday's claim after
 * midnight would simply be re-windowed into the new day. Whether the NEW
 * day's claim happens is the auto-open engine's decision, not a stale
 * queue's.
 *
 * Duplicate-retry safety (verified against the backend claim transaction):
 * claimDailyEntries dedups server-side by the canonical user's local-day
 * entry window and `daily_last_claimed === today`, throwing an
 * `already-exists` callable error ("Already claimed…" / "You've already
 * entered today…"). A duplicate retry therefore can never double-grant; an
 * already-claimed rejection is treated as SUCCESS by the retry handler.
 *
 * Mirrors the iOS reference implementation
 * (AvafliSDK/Services/Offline/OfflineResilience.swift).
 */

// ── Network error classification ──

/**
 * Splits NETWORK-class failures (the request never completed: offline,
 * timeout, connection dropped) from backend rejections (HTTP-status
 * ServerErrors, auth failures, geo-fence…). Only the former are safe to
 * retry automatically — a rejection would just be rejected again.
 */
internal object OfflineErrorClassifier {

    fun isRetriable(e: Throwable): Boolean {
        // NetworkClient wraps every transport IOException (offline, DNS,
        // socket timeout, connection reset) into AvafliError.NetworkError.
        // ServerError carries a real HTTP response — a rejection, never
        // retried here (matching iOS: transport-only, not even 5xx).
        if (e is AvafliError.NetworkError) return true
        // Defensive: a raw IOException that escaped the wrapper.
        if (e !is AvafliError && e is IOException) return true
        return false
    }
}

// ── Pending intent ──

/** A registration or claim the user meant to happen but the network dropped. */
@Serializable
internal data class PendingIntent(
    val kind: Kind,
    /**
     * Local calendar day (yyyy-MM-dd, device zone) the intent was created.
     * The same-day guard drops the intent once this day ends.
     */
    val dayKey: String,
    val createdAtMs: Long,
) {
    @Serializable
    enum class Kind { REGISTRATION, CLAIM }
}

/** Result of one retry attempt, as reported by the retry handler. */
internal enum class RetryOutcome {
    /**
     * The call succeeded — or the server said "already claimed", which the
     * idempotent backend dedup makes equivalent to success.
     */
    SUCCESS,

    /** A backend rejection. Retrying would only repeat it — drop the intent. */
    PERMANENT_FAILURE,

    /** Another transport failure — keep the intent for a later trigger. */
    RETRIABLE_FAILURE,
}

// ── Storage seam ──

/**
 * Tiny string-store seam so the queue and analytics buffer persist through
 * SharedPreferences in production and an in-memory map in JVM tests.
 */
internal interface OfflineStateStore {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}

// ── Connectivity monitoring ──

internal interface ConnectivityMonitoring {
    /** Fired on a genuine lost → available transition. */
    var onConnectivityRegained: (() -> Unit)?

    /**
     * Best-effort current state. `true` until the platform reports otherwise
     * (assume-online default keeps analytics passthrough unbuffered when the
     * network state isn't known yet).
     */
    val isOnline: Boolean

    fun start()
    fun stop()
}

/** ConnectivityManager-backed connectivity listener. */
internal class AndroidConnectivityMonitor(
    context: Context,
) : ConnectivityMonitoring {

    override var onConnectivityRegained: (() -> Unit)? = null

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

    @Volatile
    private var lastAvailable: Boolean? = null

    @Volatile
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = lastAvailable
            lastAvailable = true
            // Only a genuine offline → online transition triggers retries;
            // the initial callback on registration (previous == null) does not.
            if (previous == false) {
                onConnectivityRegained?.invoke()
            }
        }

        override fun onLost(network: Network) {
            lastAvailable = false
        }
    }

    override val isOnline: Boolean
        get() = lastAvailable ?: true

    override fun start() {
        if (started) return
        started = true
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Missing ACCESS_NETWORK_STATE or an OEM quirk — degrade to the
            // foreground/backoff triggers; never crash the host app.
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Already unregistered.
        }
    }
}

// ── Retry coordinator ──

/**
 * Persists pending register/claim intents and retries them on connectivity
 * regain, activity resume, and a capped exponential backoff while the app
 * runs. HARD caps everywhere: at most [MAX_ATTEMPTS_PER_SESSION] attempts per
 * intent kind per process lifetime, and the backoff job runs a finite
 * schedule then exits — nothing unbounded, and the job ends early the moment
 * the queue is empty.
 */
internal class OfflineRetryCoordinator(
    private val store: OfflineStateStore,
    packageName: String,
    private val scope: CoroutineScope,
    private val logger: Logger? = null,
    private val dayKeyProvider: () -> String = { LocalDate.now().toString() },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val backoffDelaysMs: List<Long> = DEFAULT_BACKOFF_DELAYS_MS,
) {

    companion object {
        const val MAX_ATTEMPTS_PER_SESSION = 5

        /** Finite backoff schedule — 5 slots, the session attempt cap. */
        val DEFAULT_BACKOFF_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L)

        private val json = Json { ignoreUnknownKeys = true }
    }

    // Same winr_ + package-suffix namespace as the SDK's other persisted keys
    // (wire/storage compat is intentionally pre-rebrand).
    private val storageKey = "winr_offline_pending_intents_$packageName"

    /** Performs the actual retry for a kind. Set once at wiring time. */
    var retryHandler: (suspend (PendingIntent.Kind) -> RetryOutcome)? = null

    private val lock = Any()
    private val attemptsThisSession = mutableMapOf<PendingIntent.Kind, Int>()
    private var backoffJob: Job? = null
    private var passJob: Job? = null

    // ── Queue ──

    /**
     * Records a pending intent (one per kind — re-enqueueing refreshes the
     * day key) and arms the in-session backoff retry job.
     */
    fun enqueue(kind: PendingIntent.Kind) {
        synchronized(lock) {
            val intents = loadIntentsLocked().filterNot { it.kind == kind } +
                PendingIntent(kind, dayKeyProvider(), nowMs())
            saveIntentsLocked(intents)
        }
        logger?.info("Offline retry queued: $kind")
        scheduleBackoffIfNeeded()
    }

    fun clear(kind: PendingIntent.Kind) {
        synchronized(lock) {
            saveIntentsLocked(loadIntentsLocked().filterNot { it.kind == kind })
        }
    }

    /** Currently pending kinds, after the same-day guard pruned stale ones. */
    val pendingKinds: List<PendingIntent.Kind>
        get() = synchronized(lock) { pruneLocked().map { it.kind } }

    fun attemptCount(kind: PendingIntent.Kind): Int =
        synchronized(lock) { attemptsThisSession[kind] ?: 0 }

    // ── Triggers ──

    /** Connectivity regained (platform callback) — retry immediately. */
    fun noteConnectivityRegained() = attemptNow()

    /** App came to the foreground — retry immediately. */
    fun noteForeground() = attemptNow()

    /** App launch — prune stale intents, then retry whatever survived. */
    fun noteLaunch() = attemptNow()

    // ── Internals ──

    private fun attemptNow() {
        synchronized(lock) {
            if (passJob?.isActive == true) return
            if (pruneLocked().isEmpty()) return
            passJob = scope.launch { performPass() }
        }
    }

    /**
     * One retry pass over the pending kinds. Every attempt counts toward the
     * hard per-session cap regardless of which trigger fired it.
     */
    private suspend fun performPass() {
        val handler = retryHandler ?: return
        for (kind in pendingKinds) {
            val allowed = synchronized(lock) {
                val attempts = attemptsThisSession[kind] ?: 0
                if (attempts >= MAX_ATTEMPTS_PER_SESSION) {
                    false
                } else {
                    attemptsThisSession[kind] = attempts + 1
                    true
                }
            }
            if (!allowed) continue

            when (handler(kind)) {
                RetryOutcome.SUCCESS -> {
                    logger?.info("Offline retry succeeded: $kind")
                    clear(kind)
                }
                RetryOutcome.PERMANENT_FAILURE -> {
                    logger?.info("Offline retry permanently rejected: $kind — dropping")
                    clear(kind)
                }
                RetryOutcome.RETRIABLE_FAILURE ->
                    logger?.debug("Offline retry still failing: $kind")
            }
        }
    }

    /**
     * Arms the capped exponential-backoff retry job. The schedule is finite
     * (5 slots, ~62s total) and the job exits the moment the queue empties or
     * the session cap is reached — never an unbounded watcher.
     */
    private fun scheduleBackoffIfNeeded() {
        synchronized(lock) {
            if (backoffJob?.isActive == true) return
            backoffJob = scope.launch {
                for (delayMs in backoffDelaysMs) {
                    delay(delayMs)
                    if (pendingKinds.isEmpty()) break
                    if (allKindsCapped()) break
                    performPass()
                }
            }
        }
    }

    private fun allKindsCapped(): Boolean = synchronized(lock) {
        PendingIntent.Kind.entries.all {
            (attemptsThisSession[it] ?: 0) >= MAX_ATTEMPTS_PER_SESSION
        }
    }

    /** Cancels in-flight jobs (configure-time rebuilds and tests). */
    fun shutdown() {
        synchronized(lock) {
            backoffJob?.cancel()
            passJob?.cancel()
            backoffJob = null
            passJob = null
        }
    }

    // ── Persistence (call under lock) ──

    private fun loadIntentsLocked(): List<PendingIntent> = try {
        store.getString(storageKey)?.let {
            json.decodeFromString(ListSerializer(PendingIntent.serializer()), it)
        } ?: emptyList()
    } catch (_: Exception) {
        // Corrupt value — drop it rather than crash forever.
        store.remove(storageKey)
        emptyList()
    }

    private fun saveIntentsLocked(intents: List<PendingIntent>) {
        if (intents.isEmpty()) {
            store.remove(storageKey)
        } else {
            store.putString(
                storageKey,
                json.encodeToString(ListSerializer(PendingIntent.serializer()), intents),
            )
        }
    }

    /**
     * SAME-DAY GUARD: drops any intent whose local calendar day has ended.
     * The server would re-window a stale claim into the new day anyway
     * (server-authoritative day windows — governed anti-fraud contract), and
     * initiating a NEW day's claim is the auto-open engine's job, not ours.
     */
    private fun pruneLocked(): List<PendingIntent> {
        val today = dayKeyProvider()
        val intents = loadIntentsLocked()
        val fresh = intents.filter { it.dayKey == today }
        if (fresh.size != intents.size) {
            logger?.info(
                "Offline retry: dropped ${intents.size - fresh.size} stale (previous-day) intent(s)"
            )
            saveIntentsLocked(fresh)
        }
        return fresh
    }
}

// ── Offline analytics buffering ──

/**
 * One buffered publisher-facing analytics event, with its ORIGINAL timestamp
 * so a flush after reconnect doesn't shift the publisher's timeline.
 */
@Serializable
internal data class BufferedAnalyticsEvent(
    val name: String,
    /** JSON-encoded params (primitives; other values degrade to strings). */
    val paramsJson: String? = null,
    val timestampMs: Long,
)

/**
 * Wraps the publisher's [AnalyticsAdapter]. While offline, `trackEvent`
 * emissions land in a bounded, persisted ring buffer (capacity 100 — oldest
 * dropped first) and are replayed in order on connectivity regain / next
 * launch, each carrying `original_timestamp` (ISO-8601) and
 * `original_timestamp_ms`. Screen views and user properties pass through
 * unbuffered (they are state, not events).
 */
internal class BufferingAnalyticsAdapter(
    private val inner: AnalyticsAdapter,
    private val store: OfflineStateStore,
    packageName: String,
    private val isOnline: () -> Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AnalyticsAdapter {

    companion object {
        const val CAPACITY = 100

        private val json = Json { ignoreUnknownKeys = true }
    }

    private val storageKey = "winr_offline_analytics_buffer_$packageName"
    private val lock = Any()

    override fun trackEvent(name: String, params: Map<String, Any?>) {
        if (isOnline()) {
            // Preserve ordering: anything buffered from an offline stretch
            // flushes BEFORE the live event goes through.
            flush()
            inner.trackEvent(name, params)
        } else {
            buffer(name, params)
        }
    }

    override fun trackScreenView(screenName: String) = inner.trackScreenView(screenName)

    override fun setUserProperty(key: String, value: String) = inner.setUserProperty(key, value)

    /**
     * Replays the buffered events to the wrapped adapter, oldest first.
     * Called on connectivity regain, on launch, and before any live event.
     */
    fun flush() {
        val events = synchronized(lock) {
            val loaded = loadBufferLocked()
            if (loaded.isNotEmpty()) store.remove(storageKey)
            loaded
        }
        for (event in events) {
            val params = decodeParams(event.paramsJson).toMutableMap()
            params["original_timestamp"] = Instant.ofEpochMilli(event.timestampMs).toString()
            params["original_timestamp_ms"] = event.timestampMs
            inner.trackEvent(event.name, params)
        }
    }

    internal val bufferedCount: Int
        get() = synchronized(lock) { loadBufferLocked().size }

    private fun buffer(name: String, params: Map<String, Any?>) {
        synchronized(lock) {
            var events = loadBufferLocked() +
                BufferedAnalyticsEvent(name, encodeParams(params), nowMs())
            // Bounded ring buffer — drop oldest beyond capacity. HARD cap.
            if (events.size > CAPACITY) {
                events = events.takeLast(CAPACITY)
            }
            store.putString(
                storageKey,
                json.encodeToString(ListSerializer(BufferedAnalyticsEvent.serializer()), events),
            )
        }
    }

    private fun loadBufferLocked(): List<BufferedAnalyticsEvent> = try {
        store.getString(storageKey)?.let {
            json.decodeFromString(ListSerializer(BufferedAnalyticsEvent.serializer()), it)
        } ?: emptyList()
    } catch (_: Exception) {
        store.remove(storageKey)
        emptyList()
    }

    private fun encodeParams(params: Map<String, Any?>): String? {
        if (params.isEmpty()) return null
        val obj = JsonObject(
            params.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    // Non-primitive values degrade to their descriptions.
                    else -> JsonPrimitive(value.toString())
                }
            }
        )
        return obj.toString()
    }

    private fun decodeParams(paramsJson: String?): Map<String, Any?> {
        if (paramsJson == null) return emptyMap()
        return try {
            json.parseToJsonElement(paramsJson).jsonObject.mapValues { (_, element) ->
                val primitive = (element as? JsonPrimitive) ?: return@mapValues element.toString()
                when {
                    primitive is JsonNull -> null
                    primitive.isString -> primitive.content
                    primitive.booleanOrNull != null -> primitive.booleanOrNull
                    primitive.longOrNull != null -> primitive.longOrNull
                    primitive.doubleOrNull != null -> primitive.doubleOrNull
                    else -> primitive.content
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
