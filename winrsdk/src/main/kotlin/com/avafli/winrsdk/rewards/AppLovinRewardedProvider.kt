package com.avafli.winrsdk.rewards

import android.app.Activity

/**
 * AppLovin MAX rewarded video provider.
 * Requires AppLovin SDK dependency in the host app.
 */
class AppLovinRewardedProvider : RewardedVideoProvider {

    override val providerName: String = "applovin"
    override var isAdReady: Boolean = false
        private set

    private var adUnitId: String? = null

    override fun initialize(activity: Activity, appKey: String?, adUnitId: String?, testMode: Boolean) {
        this.adUnitId = adUnitId
        try {
            val appLovinSdkClass = Class.forName("com.applovin.sdk.AppLovinSdk")
            val initMethod = appLovinSdkClass.getMethod("initializeSdk", android.content.Context::class.java)
            initMethod.invoke(null, activity)
        } catch (e: ClassNotFoundException) {
            // AppLovin not available
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
        callback.onAdFailedToShow("AppLovin integration requires host app setup")
    }
}
