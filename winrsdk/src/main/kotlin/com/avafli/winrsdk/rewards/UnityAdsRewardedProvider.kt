package com.avafli.winrsdk.rewards

import android.app.Activity

/**
 * Unity Ads rewarded video provider.
 * Requires Unity Ads SDK dependency in the host app.
 */
class UnityAdsRewardedProvider : RewardedVideoProvider {

    override val providerName: String = "unity"
    override var isAdReady: Boolean = false
        private set

    private var adUnitId: String? = null

    override fun initialize(activity: Activity, appKey: String?, adUnitId: String?, testMode: Boolean) {
        this.adUnitId = adUnitId
        val key = appKey ?: return
        try {
            val unityAdsClass = Class.forName("com.unity3d.ads.UnityAds")
            val initMethod = unityAdsClass.getMethod(
                "initialize",
                android.content.Context::class.java,
                String::class.java,
                Boolean::class.java
            )
            initMethod.invoke(null, activity, key, testMode)
        } catch (e: ClassNotFoundException) {
            // Unity Ads not available
        }
    }

    override fun loadAd(activity: Activity) {
        isAdReady = false
    }

    override fun showAd(activity: Activity, callback: RewardedVideoCallback) {
        if (!isAdReady) {
            callback.onAdFailedToShow("Ad not ready")
            return
        }
        callback.onAdFailedToShow("Unity Ads integration requires host app setup")
    }
}
