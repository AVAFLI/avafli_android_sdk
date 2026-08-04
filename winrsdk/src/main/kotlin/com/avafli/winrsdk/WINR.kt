package com.avafli.winrsdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.SdkConfig
import com.avafli.winrsdk.network.NetworkClient
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.services.PushNotificationManager
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.storage.SecureStorage
import com.avafli.winrsdk.ui.WINRExperienceActivity
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.util.TimeZone

/**
 * Main public API for the WINR SDK.
 * Singleton — initialize once via [configure]; the V2 experience then auto-opens
 * on the first app-open of each calendar day. Auto-open is the only way the
 * experience appears — there is no public launch API.
 */
object WINR {

    /** All-zeros UUID returned by the Ad ID provider when the user has opted out. */
    private const val OPT_OUT_AD_ID = "00000000-0000-0000-0000-000000000000"

    private var config: WINRConfiguration? = null
    private var secureStorage: SecureStorage? = null
    private var preferencesStorage: PreferencesStorage? = null
    private var networkClient: NetworkClient? = null
    private var api: WinrApi? = null
    private var logger: Logger? = null
    private var pushManager: PushNotificationManager? = null
    private var cachedGiveaway: Giveaway? = null
    private var cachedSdkConfig: SdkConfig? = null
    private var pendingCallback: ((Result<DailyEntryGrant>) -> Unit)? = null

    /**
     * Cached "publisher suspended" flag. Set when device registration fails with a
     * suspended/revoked error so repeat launches short-circuit without re-hitting the
     * backend. When true, the experience is never presented.
     */
    @Volatile
    private var isSuspended: Boolean = false

    /**
     * RTD opt-out — from the backend or the persisted local flag. Once true the
     * experience is never presented.
     */
    @Volatile
    private var cachedOptedOut: Boolean = false

    /**
     * Backend truth for whether this person has confirmed email + consent
     * (drives the unregistered impression cap for auto-present).
     */
    @Volatile
    private var cachedEmailConsent: Boolean? = null

    /** Registration finished (success or failure) — auto-present waits for it. */
    @Volatile
    private var registrationComplete: Boolean = false

    private var currentActivity: WeakReference<Activity>? = null
    private var lifecycleCallbacksRegistered = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Lenient parser for extracting structured error codes from server error bodies. */
    private val suspendedJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Configure the WINR SDK.
     *
     * This is the single entry point — call once at app launch. Registers the
     * device, then auto-presents the experience (at most once per calendar day)
     * per the V2 flow. Auto-open is ALWAYS on unless the server kill-switch
     * (sdkConfig.experience.autoOpenEnabled) turns it off.
     *
     * @param configuration A [WINRConfiguration] with your API key, environment, and options.
     */
    fun configure(configuration: WINRConfiguration) {
        val appContext = configuration.context.applicationContext

        // Store config with application context
        val resolvedConfig = configuration.copy(context = appContext)

        this.config = resolvedConfig
        this.logger = Logger(resolvedConfig.isDebug)
        this.secureStorage = SecureStorage(appContext)
        this.preferencesStorage = PreferencesStorage(appContext)
        this.networkClient = NetworkClient(resolvedConfig, secureStorage!!, logger!!)
        this.api = WinrApi(networkClient!!, logger!!)
        this.pushManager = PushNotificationManager(api!!, logger!!)

        // Re-check suspension state on every configure — a previously suspended
        // publisher may have been re-enabled since the last launch.
        isSuspended = false
        registrationComplete = false
        // Restore the persisted RTD flag so an opted-out user stays suppressed
        // even before (or without) a network round-trip.
        cachedOptedOut = preferencesStorage?.isOptedOut() ?: false

        logger?.info("WINR SDK configured (env: ${resolvedConfig.environment.name}, debug: ${resolvedConfig.isDebug})")

        // Auto-present on activity resumes too (covers the "app stayed in memory
        // overnight" case — a new day should re-open the experience).
        registerLifecycleCallbacks(appContext)

        // Register device and submit user profile in background
        val user = resolvedConfig.user
        logger?.debug("User set: ${user.id}")

        scope.launch {
            try {
                registerDeviceIfNeeded(appContext)
            } catch (e: WINRError.ServiceUnavailable) {
                // Publisher suspended/revoked — cache so repeat launches short-circuit and
                // notify any custom-UI callback awaiting the result. Not logged as an
                // error: this is an expected degrade.
                isSuspended = true
                logger?.info("Publisher account suspended; WINR experience unavailable")
                consumePendingCallback()?.invoke(Result.failure(e))
            } catch (e: Exception) {
                logger?.error("Background device registration failed: ${e.message}", e)
            }
            registrationComplete = true
            autoPresentIfEligible()

            // Submit user profile once we have a token
            if (secureStorage?.getToken() != null) {
                try {
                    val maidId = getAdvertisingId()
                    api?.submitUserProfile(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        phone = user.phone,
                        smsConsent = false,
                        maidId = maidId,
                        publisherUserId = user.id
                    )
                } catch (e: Exception) {
                    logger?.error("Failed to submit user profile: ${e.message}", e)
                }
            }
        }
    }

    // ── Auto-present (V2 experience: open once per calendar day on app open) ──

    private fun registerLifecycleCallbacks(appContext: Context) {
        if (lifecycleCallbacksRegistered) return
        val application = appContext as? Application ?: return
        lifecycleCallbacksRegistered = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is WINRExperienceActivity) return
                currentActivity = WeakReference(activity)
                autoPresentIfEligible()
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Presents the experience automatically, at most once per calendar day, when
     * all conditions allow. Called after registration completes and on each
     * activity resume. All short-circuits are silent by design:
     * - server kill-switch: sdkConfig.experience.autoOpenEnabled
     * - unregistered (no email) users: at most experience.unregisteredImpressionCap
     *   (default 3) auto-opens ever, then silence until registered
     * - opted-out (RTD) users never see it
     */
    private fun autoPresentIfEligible() {
        if (config == null || !registrationComplete || isSuspended || cachedOptedOut) return
        val experience = cachedSdkConfig?.experience
        if (experience?.autoOpenEnabled == false) return
        if (cachedGiveaway == null) return
        val prefs = preferencesStorage ?: return

        // Once per calendar day (mark keyed by package name inside prefs).
        val today = LocalDate.now().toString()
        if (prefs.getLastAutoPresentDay() == today) return

        // Unregistered users (no confirmed email) see the auto-open at most N
        // times (default 3 per the MVP decision), then the SDK goes quiet until
        // they register.
        if (cachedEmailConsent != true) {
            val cap = experience?.unregisteredImpressionCap ?: 3
            val seen = prefs.getUnregisteredImpressions()
            if (seen >= cap) {
                logger?.debug("Auto-present skipped: unregistered impression cap ($cap) reached")
                return
            }
            prefs.saveUnregisteredImpressions(seen + 1)
        }

        val activity = currentActivity?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed || activity is WINRExperienceActivity) return

        prefs.saveLastAutoPresentDay(today)
        logger?.info("Auto-presenting WINR experience (first open of the day)")
        present(activity)
    }

    /**
     * Presentation path used exclusively by the auto-open engine
     * ([autoPresentIfEligible]). Not part of the public API — the experience is
     * only ever opened by the SDK itself, at most once per calendar day.
     *
     * @param activity The activity from which to present
     * @param callback Called with the result of the daily entry claim
     */
    internal fun present(activity: Activity, callback: ((Result<DailyEntryGrant>) -> Unit)? = null) {
        if (config == null) {
            callback?.invoke(Result.failure(WINRError.NotInitialized()))
            return
        }

        // If the publisher has been suspended/revoked, silently decline to present.
        if (isSuspended) {
            logger?.info("Publisher suspended; declining to present WINR experience")
            callback?.invoke(Result.failure(WINRError.ServiceUnavailable()))
            return
        }

        // RTD: an opted-out person never sees the experience again.
        if (cachedOptedOut) {
            logger?.info("WINR present suppressed: user opted out (RTD)")
            callback?.invoke(Result.failure(WINRError.OptedOut()))
            return
        }

        this.pendingCallback = callback

        val intent = Intent(activity, WINRExperienceActivity::class.java)
        activity.startActivity(intent)
        // The V2 drawer animates itself (slide-up spring inside a transparent
        // activity) — suppress the system activity transition.
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    /**
     * Whether the WINR experience is currently available.
     *
     * Returns false once device registration has failed with a suspended/revoked
     * error, or when the user has opted out (RTD). Internal — auto-open makes
     * this decision itself; there is no publisher-facing availability gate.
     */
    internal fun isServiceAvailable(): Boolean = !isSuspended && !cachedOptedOut

    /**
     * Right-To-Delete opt-out: tombstones the person on the backend (identity-wide,
     * PII anonymized, email suppressed) and permanently silences the experience on
     * this device. Wire this to the opt-out action in your privacy-policy flow.
     */
    suspend fun optOut(): Result<Unit> {
        val currentApi = api ?: return Result.failure(WINRError.NotInitialized())
        return try {
            currentApi.optOut()
            cachedOptedOut = true
            preferencesStorage?.saveOptedOut(true)
            logger?.info("User opted out of WINR (RTD) — experience permanently silenced")
            Result.success(Unit)
        } catch (e: Exception) {
            logger?.error("Opt-out failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Register for push notifications (streak reminders).
     * The host app must have Firebase Cloud Messaging configured.
     * No-op if [WINROptions.enablePushReminders] is false.
     */
    fun registerForPushNotifications(context: Context) {
        if (config?.options?.enablePushReminders != true) {
            logger?.debug("Push reminders disabled in options")
            return
        }
        pushManager?.getCurrentToken(context) { token ->
            token?.let { onNewToken(it) }
        }
    }

    /**
     * Called when a new FCM token is received.
     * Call this from your FirebaseMessagingService.onNewToken().
     */
    fun onNewToken(token: String) {
        if (config?.options?.enablePushReminders != true) return
        pushManager?.registerToken(token)
    }

    /**
     * Delete all user data (GDPR right-to-be-forgotten).
     */
    suspend fun deleteAccount(): Result<Unit> {
        val currentApi = api ?: return Result.failure(WINRError.NotInitialized())

        return try {
            currentApi.deleteUserData()
            secureStorage?.clearAll()
            preferencesStorage?.clearAll()
            cachedGiveaway = null
            cachedSdkConfig = null
            cachedEmailConsent = null
            isSuspended = false
            logger?.info("User account deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            logger?.error("Failed to delete account: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Internal API for Activity/ViewModel ---

    internal fun createExperienceViewModel(): WINRExperienceViewModel? {
        val currentApi = api ?: return null
        val currentPrefs = preferencesStorage ?: return null
        val currentLogger = logger ?: return null

        return WINRExperienceViewModel(
            currentApi,
            currentPrefs,
            currentLogger,
            config?.options?.analyticsAdapter
        )
    }

    internal fun getSdkConfig(): SdkConfig? = cachedSdkConfig

    internal fun getCachedGiveaway(): Giveaway? = cachedGiveaway

    /** The experience keeps the SDK-level config cache fresh after each load. */
    internal fun updateSdkConfig(sdkConfig: SdkConfig) {
        cachedSdkConfig = sdkConfig
    }

    /**
     * The experience confirmed email consent (submit or backend echo) — keeps the
     * unregistered-impression counter from consuming further auto-opens.
     */
    internal fun noteEmailConsent() {
        cachedEmailConsent = true
    }

    internal fun consumePendingCallback(): ((Result<DailyEntryGrant>) -> Unit)? {
        val cb = pendingCallback
        pendingCallback = null
        return cb
    }

    internal fun getPublisherUserId(): String? = config?.user?.id

    /** Host-app-provided identity — prefills the winner prize-claim form. */
    internal fun getConfiguredUser(): WINRUser? = config?.user

    // --- Private helpers ---

    private suspend fun registerDeviceIfNeeded(context: Context) {
        // Skip if we already have a valid token
        if (secureStorage?.getToken() != null) {
            logger?.debug("Device already registered, skipping")
            // Fetch latest giveaway + sdkConfig + claim/consent status
            try {
                val response = api?.getActiveGiveaway()
                cachedGiveaway = response?.giveaway
                cachedSdkConfig = response?.sdkConfig ?: cachedSdkConfig
                cachedEmailConsent = response?.emailConsentStatus
                if (response?.optedOut == true) {
                    cachedOptedOut = true
                    preferencesStorage?.saveOptedOut(true)
                }
            } catch (e: WINRError) {
                if (isSuspendedError(e)) throw WINRError.ServiceUnavailable()
                logger?.warn("Failed to refresh giveaway: ${e.message}")
            } catch (e: Exception) {
                logger?.warn("Failed to refresh giveaway: ${e.message}")
            }
            return
        }

        val currentApi = api ?: throw WINRError.NotInitialized()
        val apiKey = config?.apiKey ?: throw WINRError.InvalidPublisherKey()
        val deviceFingerprint = getDeviceFingerprint(context)
        val bundleId = context.packageName
        val timezone = TimeZone.getDefault().id

        logger?.debug("Registering device...")

        val response = try {
            currentApi.registerDevice(
                apiKey = apiKey,
                deviceFingerprint = deviceFingerprint,
                bundleId = bundleId,
                timezone = timezone
            )
        } catch (e: WINRError) {
            // Map a suspended/revoked publisher into the dedicated ServiceUnavailable error
            // so the caller can degrade gracefully.
            if (isSuspendedError(e)) {
                throw WINRError.ServiceUnavailable()
            }
            throw e
        }

        secureStorage?.saveToken(response.token)
        secureStorage?.saveRefreshToken(response.refreshToken)
        secureStorage?.saveUuid(response.uuid)

        cachedGiveaway = response.giveaway
        cachedSdkConfig = response.sdkConfig
        if (response.optedOut == true) {
            cachedOptedOut = true
            preferencesStorage?.saveOptedOut(true)
        }

        // Do not log the uuid (PII-linked identifier) at info level.
        logger?.info("Device registered successfully")
        logger?.debug("Registered uuid present: ${response.uuid.isNotEmpty()}")
    }

    /**
     * Determine whether [e] represents a suspended/revoked publisher rejection.
     *
     * Prefers the server's structured error code/status (parsed from the ServerError JSON
     * body) and falls back to message-substring matching ("suspended" / "revoked") only as
     * a last resort, mirroring the detection strategy used for already-claimed errors.
     */
    private fun isSuspendedError(e: Throwable): Boolean {
        // Already mapped.
        if (e is WINRError.ServiceUnavailable) return true

        // Structured server error: parse the JSON body for an error code/status field.
        if (e is WINRError.ServerError) {
            val structured = try {
                val body = e.message ?: ""
                val start = body.indexOf('{')
                if (start >= 0) {
                    val obj = suspendedJson.parseToJsonElement(body.substring(start)).jsonObject
                    val code = (obj["error"]?.jsonObject?.get("status")
                        ?: obj["error"]?.jsonObject?.get("code")
                        ?: obj["code"]
                        ?: obj["status"])?.jsonPrimitive?.contentOrNull
                    code?.uppercase()?.let {
                        it == "PERMISSION_DENIED" || it == "SUSPENDED" || it == "REVOKED"
                    } ?: false
                } else false
            } catch (_: Exception) {
                false
            }
            if (structured) return true
        }

        // Last-resort fallback: legacy message-substring match.
        val msg = e.message ?: return false
        return msg.contains("suspended", ignoreCase = true) ||
            msg.contains("revoked", ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun getDeviceFingerprint(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    private suspend fun getAdvertisingId(): String? = withContext(Dispatchers.IO) {
        try {
            val adIdClientClass = Class.forName(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient"
            )
            val getInfoMethod = adIdClientClass.getMethod(
                "getAdvertisingIdInfo",
                Context::class.java
            )
            val info = getInfoMethod.invoke(null, config?.context)

            // Respect the user's limit-ad-tracking choice. If LAT is enabled we must not
            // collect the GAID (spec: maid_id only collected when GAID is granted).
            val isLatMethod = info?.javaClass?.getMethod("isLimitAdTrackingEnabled")
            val limitAdTracking = isLatMethod?.invoke(info) as? Boolean ?: true
            if (limitAdTracking) {
                logger?.debug("Limit-ad-tracking enabled; omitting GAID")
                return@withContext null
            }

            val getIdMethod = info?.javaClass?.getMethod("getId")
            val adId = getIdMethod?.invoke(info) as? String

            // The all-zeros UUID is the documented opt-out sentinel; treat as unavailable.
            if (adId == null || adId == OPT_OUT_AD_ID) {
                logger?.debug("GAID unavailable or opt-out sentinel; omitting")
                null
            } else {
                adId
            }
        } catch (e: Exception) {
            logger?.debug("AdvertisingId not available: ${e.message}")
            null
        }
    }
}
