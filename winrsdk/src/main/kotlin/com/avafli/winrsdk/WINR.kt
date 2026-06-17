package com.avafli.winrsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.SdkConfig
import com.avafli.winrsdk.network.NetworkClient
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.rewards.AdProviderFactory
import com.avafli.winrsdk.rewards.RewardedVideoProvider
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
import java.util.TimeZone

/**
 * Main public API for the WINR SDK.
 * Singleton — initialize once, then call present() to show the experience.
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
    private var adProvider: RewardedVideoProvider? = null
    private var cachedGiveaway: Giveaway? = null
    private var cachedSdkConfig: SdkConfig? = null
    private var pendingCallback: ((Result<DailyEntryGrant>) -> Unit)? = null

    /**
     * Cached "publisher suspended" flag. Set when device registration fails with a
     * suspended/revoked error so repeat launches short-circuit without re-hitting the
     * backend. When true, the default UI is never presented.
     */
    @Volatile
    private var isSuspended: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Lenient parser for extracting structured error codes from server error bodies. */
    private val suspendedJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Configure the WINR SDK.
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

        logger?.info("WINR SDK configured (env: ${resolvedConfig.environment.name}, debug: ${resolvedConfig.isDebug})")

        // Register device and submit user profile in background
        val user = resolvedConfig.user
        logger?.debug("User set: ${user.id}")

        scope.launch {
            try {
                registerDeviceIfNeeded(appContext)
            } catch (e: WINRError.ServiceUnavailable) {
                // Publisher suspended/revoked — cache so repeat launches short-circuit and
                // notify any custom-UI callback awaiting the result. Default UI is declined
                // in present(). Not logged as an error: this is an expected degrade.
                isSuspended = true
                logger?.info("Publisher account suspended; WINR experience unavailable")
                consumePendingCallback()?.invoke(Result.failure(e))
            } catch (e: Exception) {
                logger?.error("Background device registration failed: ${e.message}", e)
            }

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

    /**
     * Present the WINR experience.
     *
     * @param activity The activity from which to present
     */
    fun present(activity: Activity) {
        present(activity, null)
    }

    /**
     * Present the WINR experience with a result callback.
     *
     * @param activity The activity from which to present
     * @param callback Called with the result of the daily entry claim
     */
    fun present(activity: Activity, callback: ((Result<DailyEntryGrant>) -> Unit)? = null) {
        if (config == null) {
            callback?.invoke(Result.failure(WINRError.NotInitialized()))
            return
        }

        // Default-UI path: if the publisher has been suspended/revoked, silently decline to
        // present the WINR experience. Report ServiceUnavailable via the callback and return
        // without launching the Activity. This is a clean degrade — no crash, no empty UI.
        if (isSuspended) {
            logger?.info("Publisher suspended; declining to present WINR experience")
            callback?.invoke(Result.failure(WINRError.ServiceUnavailable()))
            return
        }

        this.pendingCallback = callback

        val intent = Intent(activity, WINRExperienceActivity::class.java)
        activity.startActivity(intent)
    }

    /**
     * Whether the WINR experience is currently available for this publisher.
     *
     * Returns false once device registration has failed with a suspended/revoked error
     * (e.g. a lapsed billing account). Publishers using a custom launch UI can query this
     * to hide their WINR entry point or show their own "no longer available" messaging
     * instead of calling [present].
     */
    fun isServiceAvailable(): Boolean = !isSuspended

    /**
     * Register for push notifications.
     * The host app must have Firebase Cloud Messaging configured.
     */
    fun registerForPushNotifications(context: Context) {
        pushManager?.getCurrentToken(context) { token ->
            token?.let { onNewToken(it) }
        }
    }

    /**
     * Called when a new FCM token is received.
     * Call this from your FirebaseMessagingService.onNewToken().
     */
    fun onNewToken(token: String) {
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

        return WINRExperienceViewModel(currentApi, currentPrefs, currentLogger)
    }

    internal fun getBranding(): WINRBranding {
        val baseBranding = config?.branding ?: WINRBranding()
        return baseBranding.applyingServerBranding(cachedSdkConfig?.branding)
    }

    internal fun getSdkConfig(): SdkConfig? = cachedSdkConfig

    internal fun getCachedGiveaway(): Giveaway? = cachedGiveaway

    internal fun consumePendingCallback(): ((Result<DailyEntryGrant>) -> Unit)? {
        val cb = pendingCallback
        pendingCallback = null
        return cb
    }

    internal fun getPublisherUserId(): String? = config?.user?.id

    internal fun getAdProvider(): RewardedVideoProvider? = adProvider

    // --- Private helpers ---

    private suspend fun registerDeviceIfNeeded(context: Context) {
        // Skip if we already have a valid token
        if (secureStorage?.getToken() != null) {
            logger?.debug("Device already registered, skipping")
            // Fetch latest giveaway + sdkConfig
            try {
                val activeGiveawayResponse = api?.getActiveGiveaway()
                cachedGiveaway = activeGiveawayResponse?.giveaway
                cachedSdkConfig = activeGiveawayResponse?.sdkConfig ?: cachedSdkConfig
                setupAdProvider()
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
            // so the caller can degrade gracefully. The backend surfaces this as an error
            // whose message contains "suspended" (e.g. "API key suspended or revoked" /
            // "Publisher account suspended").
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
        setupAdProvider()

        // Do not log the uuid (PII-linked identifier) at info level.
        logger?.info("Device registered successfully")
        logger?.debug("Registered uuid present: ${response.uuid.isNotEmpty()}")
    }

    /**
     * Determine whether [e] represents a suspended/revoked publisher rejection raised
     * during device registration.
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

    private fun setupAdProvider() {
        val adConfig = cachedGiveaway?.adConfig ?: return
        adProvider = AdProviderFactory.createProvider(adConfig)
        logger?.debug("Ad provider configured: ${adConfig.provider}")
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
