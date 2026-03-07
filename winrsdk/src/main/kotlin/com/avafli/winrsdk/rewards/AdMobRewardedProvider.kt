package com.avafli.winrsdk.rewards

import android.app.Activity

/**
 * AdMob rewarded video provider.
 * Requires com.google.android.gms:play-services-ads dependency in the host app.
 */
class AdMobRewardedProvider : RewardedVideoProvider {

    override val providerName: String = "admob"
    override var isAdReady: Boolean = false
        private set

    private var adUnitId: String? = null

    override fun initialize(activity: Activity, appKey: String?, adUnitId: String?, testMode: Boolean) {
        this.adUnitId = adUnitId
        try {
            // Initialize via reflection to avoid hard dependency
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")
            val initMethod = mobileAdsClass.getMethod("initialize", android.content.Context::class.java)
            initMethod.invoke(null, activity)
        } catch (e: ClassNotFoundException) {
            // AdMob not available
        }
    }

    override fun loadAd(activity: Activity) {
        val unitId = adUnitId ?: return
        try {
            val rewardedAdClass = Class.forName("com.google.android.gms.ads.rewarded.RewardedAd")
            val adRequestClass = Class.forName("com.google.android.gms.ads.AdRequest")
            val builderClass = Class.forName("com.google.android.gms.ads.AdRequest\$Builder")

            val builder = builderClass.getDeclaredConstructor().newInstance()
            val buildMethod = builderClass.getMethod("build")
            val adRequest = buildMethod.invoke(builder)

            // RewardedAd.load() via reflection
            // Host app should handle actual ad loading
            isAdReady = false
        } catch (e: Exception) {
            isAdReady = false
        }
    }

    override fun showAd(activity: Activity, callback: RewardedVideoCallback) {
        if (!isAdReady) {
            callback.onAdFailedToShow("Ad not ready")
            return
        }
        // Actual ad display handled via reflection or host app integration
        callback.onAdFailedToShow("AdMob integration requires host app setup")
    }
}
