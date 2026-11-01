package com.vttd.sieutrangnhi

object AdManager {
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    fun getBannerAdUnitId(): String = TEST_BANNER_ID
    fun getInterstitialAdUnitId(): String = TEST_INTERSTITIAL_ID
    fun getRewardedAdUnitId(): String = TEST_REWARDED_ID
}
