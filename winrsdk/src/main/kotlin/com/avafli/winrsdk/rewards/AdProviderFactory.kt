package com.avafli.winrsdk.rewards

import com.avafli.winrsdk.domain.AdConfig

/**
 * Factory for creating rewarded video providers based on campaign config.
 */
internal object AdProviderFactory {

    fun createProvider(adConfig: AdConfig): RewardedVideoProvider? {
        return when (adConfig.provider.lowercase()) {
            "admob" -> createIfAvailable("com.avafli.winrsdk.rewards.AdMobRewardedProvider")
            "applovin" -> createIfAvailable("com.avafli.winrsdk.rewards.AppLovinRewardedProvider")
            "ironsource" -> createIfAvailable("com.avafli.winrsdk.rewards.IronSourceRewardedProvider")
            "unity" -> createIfAvailable("com.avafli.winrsdk.rewards.UnityAdsRewardedProvider")
            else -> null
        }
    }

    private fun createIfAvailable(className: String): RewardedVideoProvider? {
        return try {
            val clazz = Class.forName(className)
            clazz.getDeclaredConstructor().newInstance() as? RewardedVideoProvider
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
