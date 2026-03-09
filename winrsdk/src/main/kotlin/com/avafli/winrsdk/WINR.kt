package com.avafli.winrsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.avafli.winrsdk.domain.Campaign
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
import java.util.TimeZone

/**
 * Main public API for the WINR SDK.
 * Singleton — initialize once, then call present() to show the experience.
 */
object WINR {

    private var config: WINRConfiguration? = null
    private var secureStorage: SecureStorage? = null
    private var preferencesStorage: PreferencesStorage? = null
    private var networkClient: NetworkClient? = null
    private var api: WinrApi? = null
    private var logger: Logger? = null
    private var pushManager: PushNotificationManager? = null
    private var adProvider: RewardedVideoProvider? = null
    private var user: WINRUser? = null
    private var cachedCampaign: Campaign? = null
    private var cachedSdkConfig: SdkConfig? = null
    private var pendingCallback: ((Result<DailyEntryGrant>) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Initialize the WINR SDK.
     *
     * @param context Application or Activity context
     * @param publisherKey Your publisher API key
     * @param environment Target environment (default: Production)
     * @param options Additional configuration options
     */
    fun initialize(
        context: Context,
        publisherKey: String,
        environment: WINREnvironment = WINREnvironment.Production,
        options: WINROptions = WINROptions()
    ) {
        val appContext = context.applicationContext

        val configuration = WINRConfiguration(
            context = appContext,
            publisherKey = publisherKey,
            environment = environment,
            options = options
        )

        this.config = configuration
        this.logger = Logger(configuration.isDebug)
        this.secureStorage = SecureStorage(appContext)
        this.preferencesStorage = PreferencesStorage(appContext)
        this.networkClient = NetworkClient(configuration, secureStorage!!, logger!!)
        this.api = WinrApi(networkClient!!, logger!!)
        this.pushManager = PushNotificationManager(api!!, logger!!)

        logger?.info("WINR SDK initialized (env: ${environment.name}, debug: ${configuration.isDebug})")

        // Register device in background
        scope.launch {
            try {
                registerDeviceIfNeeded(appContext)
            } catch (e: Exception) {
                logger?.error("Background device registration failed: ${e.message}", e)
            }
        }
    }

    /**
     * Set user information for the current session.
     */
    fun setUser(winrUser: WINRUser) {
        this.user = winrUser
        logger?.debug("User set: ${winrUser.id ?: "anonymous"}")

        // Submit user profile if we have a token
        if (secureStorage?.getToken() != null) {
            scope.launch {
                try {
                    val maidId = getAdvertisingId()
                    api?.submitUserProfile(
                        firstName = winrUser.firstName,
                        lastName = winrUser.lastName,
                        phone = winrUser.phone,
                        smsConsent = winrUser.isSMSPermissioned,
                        maidId = maidId
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

        this.pendingCallback = callback

        val intent = Intent(activity, WINRExperienceActivity::class.java)
        activity.startActivity(intent)
    }

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
            cachedCampaign = null
            cachedSdkConfig = null
            user = null
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

    internal fun getCachedCampaign(): Campaign? = cachedCampaign

    internal fun consumePendingCallback(): ((Result<DailyEntryGrant>) -> Unit)? {
        val cb = pendingCallback
        pendingCallback = null
        return cb
    }

    internal fun getAdProvider(): RewardedVideoProvider? = adProvider

    // --- Private helpers ---

    private suspend fun registerDeviceIfNeeded(context: Context) {
        // Skip if we already have a valid token
        if (secureStorage?.getToken() != null) {
            logger?.debug("Device already registered, skipping")
            // Fetch latest campaign + sdkConfig
            try {
                val activeCampaignResponse = api?.getActiveCampaign()
                cachedCampaign = activeCampaignResponse?.campaign
                cachedSdkConfig = activeCampaignResponse?.sdkConfig ?: cachedSdkConfig
                setupAdProvider()
            } catch (e: Exception) {
                logger?.warn("Failed to refresh campaign: ${e.message}")
            }
            return
        }

        val currentApi = api ?: throw WINRError.NotInitialized()
        val publisherKey = config?.publisherKey ?: throw WINRError.InvalidPublisherKey()
        val deviceFingerprint = getDeviceFingerprint(context)
        val bundleId = context.packageName
        val timezone = TimeZone.getDefault().id

        logger?.debug("Registering device...")

        val response = currentApi.registerDevice(
            apiKey = publisherKey,
            deviceFingerprint = deviceFingerprint,
            bundleId = bundleId,
            timezone = timezone
        )

        secureStorage?.saveToken(response.token)
        secureStorage?.saveRefreshToken(response.refreshToken)
        secureStorage?.saveUuid(response.uuid)

        cachedCampaign = response.campaign
        cachedSdkConfig = response.sdkConfig
        setupAdProvider()

        logger?.info("Device registered successfully (uuid: ${response.uuid})")
    }

    private fun setupAdProvider() {
        val adConfig = cachedCampaign?.adConfig ?: return
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
            val getIdMethod = info?.javaClass?.getMethod("getId")
            getIdMethod?.invoke(info) as? String
        } catch (e: Exception) {
            logger?.debug("AdvertisingId not available: ${e.message}")
            null
        }
    }
}
