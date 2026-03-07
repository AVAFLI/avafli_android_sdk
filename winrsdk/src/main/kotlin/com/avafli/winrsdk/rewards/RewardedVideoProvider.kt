package com.avafli.winrsdk.rewards

import android.app.Activity

/**
 * Interface for rewarded video ad providers.
 */
interface RewardedVideoProvider {
    /** Provider name (e.g., "admob", "applovin"). */
    val providerName: String

    /** Whether an ad is currently loaded and ready to show. */
    val isAdReady: Boolean

    /** Initialize the provider with configuration. */
    fun initialize(activity: Activity, appKey: String?, adUnitId: String?, testMode: Boolean)

    /** Load a rewarded ad. */
    fun loadAd(activity: Activity)

    /** Show the rewarded ad. */
    fun showAd(activity: Activity, callback: RewardedVideoCallback)
}

/**
 * Callback for rewarded video events.
 */
interface RewardedVideoCallback {
    fun onAdLoaded()
    fun onAdFailedToLoad(error: String)
    fun onAdShown()
    fun onAdDismissed()
    fun onRewardEarned()
    fun onAdFailedToShow(error: String)
}
