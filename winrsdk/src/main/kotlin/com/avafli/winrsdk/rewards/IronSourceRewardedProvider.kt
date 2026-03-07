package com.avafli.winrsdk.rewards

import android.app.Activity

/**
 * IronSource (LevelPlay) rewarded video provider.
 * Requires IronSource SDK dependency in the host app.
 */
class IronSourceRewardedProvider : RewardedVideoProvider {

    override val providerName: String = "ironsource"
    override var isAdReady: Boolean = false
        private set

    override fun initialize(activity: Activity, appKey: String?, adUnitId: String?, testMode: Boolean) {
        val key = appKey ?: return
        try {
            val ironSourceClass = Class.forName("com.ironsource.mediationsdk.IronSource")
            val initMethod = ironSourceClass.getMethod("init", Activity::class.java, String::class.java)
            initMethod.invoke(null, activity, key)
        } catch (e: ClassNotFoundException) {
            // IronSource not available
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
        callback.onAdFailedToShow("IronSource integration requires host app setup")
    }
}
