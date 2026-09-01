package com.avafli.avaflisdk.offline

import android.content.Context
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.services.analytics.AnalyticsAdapter
import kotlinx.coroutines.CoroutineScope

/**
 * Session-scoped owner of the offline machinery (retry coordinator +
 * connectivity monitor + analytics buffer). Built by `Avafli.configure`;
 * everything inside is individually testable without it. Mirrors the iOS
 * `AvafliOfflineResilience` shared wiring.
 */
internal object AvafliOfflineResilience {

    @Volatile
    var shared: Session? = null
        private set

    internal class Session(
        val packageName: String,
        val coordinator: OfflineRetryCoordinator,
        val monitor: ConnectivityMonitoring,
        private val store: OfflineStateStore,
    ) {
        private var analyticsWrapper: BufferingAnalyticsAdapter? = null

        /** Memoized buffering wrapper around the publisher's adapter. */
        @Synchronized
        fun analyticsAdapter(inner: AnalyticsAdapter?): AnalyticsAdapter? {
            if (inner == null) return null
            analyticsWrapper?.let { return it }
            return BufferingAnalyticsAdapter(
                inner = inner,
                store = store,
                packageName = packageName,
                isOnline = { monitor.isOnline },
            ).also { analyticsWrapper = it }
        }

        fun flushAnalyticsBuffer() {
            if (!monitor.isOnline) return
            synchronized(this) { analyticsWrapper }?.flush()
        }
    }

    /**
     * (Re)build the shared session on configure. Reused when the package is
     * unchanged so the session attempt caps aren't reset by re-configures.
     */
    fun activate(
        context: Context,
        store: OfflineStateStore,
        scope: CoroutineScope,
        logger: Logger?,
    ): Session {
        val packageName = context.applicationContext.packageName
        shared?.let { existing ->
            if (existing.packageName == packageName) return existing
            existing.monitor.stop()
            existing.coordinator.shutdown()
        }
        val monitor = AndroidConnectivityMonitor(context)
        val coordinator = OfflineRetryCoordinator(
            store = store,
            packageName = packageName,
            scope = scope,
            logger = logger,
        )
        val session = Session(packageName, coordinator, monitor, store)
        monitor.onConnectivityRegained = {
            coordinator.noteConnectivityRegained()
            session.flushAnalyticsBuffer()
        }
        monitor.start()
        shared = session
        return session
    }

    fun resetForTests() {
        shared?.monitor?.stop()
        shared?.coordinator?.shutdown()
        shared = null
    }
}
