package com.vttd.sieutrangnhi

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Class quản lý tập trung toàn bộ quảng cáo Google AdMob (Banner, Interstitial, Rewarded)
 *
 * QUAN TRỌNG - Cơ chế Test / Release:
 * - Nếu ở chế độ Debug (BuildConfig.DEBUG == true): Tự động sử dụng Mã Test ID chính thức từ Google AdMob
 *   để tránh vi phạm chính sách / khóa tài khoản khi lập trình & thử nghiệm.
 * - Khi build ở chế độ Release (BuildConfig.DEBUG == false): Tự động nạp Mã thật được lưu trong file .env.
 */
class AdManager(
  private val context: Context,
  private val vipPrefs: VipPreferenceManager
) {

  companion object {
    private const val TAG = "AdManager"

    // Mã Test Ad ID chính thức công khai của Google AdMob
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
  }

  // Tự động xác định Mã ID thật hay Mã Test dựa theo BuildConfig.DEBUG
  val bannerAdUnitId: String
    get() = if (BuildConfig.DEBUG) TEST_BANNER_ID else BuildConfig.ADMOB_BANNER_ID.ifBlank { TEST_BANNER_ID }

  val interstitialAdUnitId: String
    get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_ID else BuildConfig.ADMOB_INTERSTITIAL_ID.ifBlank { TEST_INTERSTITIAL_ID }

  val rewardedAdUnitId: String
    get() = if (BuildConfig.DEBUG) TEST_REWARDED_ID else BuildConfig.ADMOB_REWARDED_ID.ifBlank { TEST_REWARDED_ID }

  private var bannerAdView: AdView? = null
  private var interstitialAd: InterstitialAd? = null
  private var rewardedAd: RewardedAd? = null
  private var isInitialized = false

  init {
    initSdk()
  }

  /**
   * Khởi tạo Google AdMob SDK & Cấu hình tuân thủ chính sách bảo vệ trẻ em (Families Policy / G-Rated)
   */
  private fun initSdk() {
    try {
      val requestConfiguration = RequestConfiguration.Builder()
        .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
        .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
        .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
        .build()
      MobileAds.setRequestConfiguration(requestConfiguration)

      MobileAds.initialize(context) { status ->
        isInitialized = true
        Log.d(TAG, "AdMob SDK Initialized (DebugMode=${BuildConfig.DEBUG}): $status")
        Log.d(TAG, "Using Banner ID: $bannerAdUnitId")
        Log.d(TAG, "Using Interstitial ID: $interstitialAdUnitId")
        Log.d(TAG, "Using Rewarded ID: $rewardedAdUnitId")

        if (vipPrefs.shouldShowInterstitialAd()) {
          loadInterstitialAd()
        }
        loadRewardedAd()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi khởi tạo AdMob SDK: ${e.message}")
    }
  }

  private fun buildAdRequest(): AdRequest {
    val extras = Bundle().apply {
      putString("npa", "1")
    }
    return AdRequest.Builder()
      .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
      .build()
  }

  // ========================================================
  // 1. QUẢNG CÁO BANNER (GOOGLE ANCHORED ADAPTIVE BANNER)
  // ========================================================

  private fun getAdaptiveAdSize(container: FrameLayout): AdSize {
    val displayMetrics = context.resources.displayMetrics
    var adWidthPixels = container.width.toFloat()
    if (adWidthPixels <= 0f) {
      adWidthPixels = displayMetrics.widthPixels.toFloat()
    }
    val density = displayMetrics.density
    val adWidth = (adWidthPixels / density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
  }

  fun initBannerAd(container: FrameLayout) {
    if (!vipPrefs.shouldShowBannerAd()) {
      container.visibility = View.GONE
      destroyBanner()
      return
    }

    try {
      val adaptiveAdSize = getAdaptiveAdSize(container)
      val adView = AdView(context).apply {
        setAdSize(adaptiveAdSize)
        adUnitId = this@AdManager.bannerAdUnitId
        adListener = object : AdListener() {
          override fun onAdLoaded() {
            Log.d(TAG, "Adaptive Banner Ad loaded successfully ($bannerAdUnitId)")
            if (vipPrefs.shouldShowBannerAd()) {
              container.visibility = View.VISIBLE
            }
          }
          override fun onAdFailedToLoad(error: LoadAdError) {
            Log.w(TAG, "Adaptive Banner Ad failed to load: ${error.message}")
          }
        }
      }
      bannerAdView = adView
      container.removeAllViews()
      container.addView(
        adView,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          Gravity.CENTER
        )
      )
      adView.loadAd(buildAdRequest())
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi nạp Adaptive Banner Ad: ${e.message}")
    }
  }

  fun updateBannerVisibility(container: FrameLayout?, visible: Boolean) {
    if (!vipPrefs.shouldShowBannerAd()) {
      container?.visibility = View.GONE
      destroyBanner()
    } else {
      container?.visibility = if (visible) View.VISIBLE else View.GONE
    }
  }

  fun destroyBanner() {
    val adView: AdView? = bannerAdView
    if (adView != null) {
      (adView.parent as? ViewGroup)?.removeView(adView)
      adView.destroy()
    }
    bannerAdView = null
  }

  // ========================================================
  // 2. QUẢNG CÁO TOÀN MÀN HÌNH (INTERSTITIAL AD)
  // ========================================================

  fun loadInterstitialAd() {
    if (!vipPrefs.shouldShowInterstitialAd()) return

    val adRequest = buildAdRequest()
    InterstitialAd.load(
      context,
      interstitialAdUnitId,
      adRequest,
      object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
          interstitialAd = ad
          Log.d(TAG, "Interstitial Ad loaded successfully ($interstitialAdUnitId)")
        }
        override fun onAdFailedToLoad(error: LoadAdError) {
          interstitialAd = null
          Log.w(TAG, "Interstitial Ad load failed: ${error.message}")
        }
      }
    )
  }

  fun showInterstitialAd(activity: Activity, onClosed: () -> Unit) {
    if (!vipPrefs.shouldShowInterstitialAd()) {
      Log.d(TAG, "Bỏ qua Interstitial Ad theo chính sách VIP Level ${vipPrefs.getVipLevel()}")
      onClosed()
      return
    }

    val ad = interstitialAd
    if (ad != null) {
      ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          interstitialAd = null
          loadInterstitialAd()
          onClosed()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          interstitialAd = null
          loadInterstitialAd()
          onClosed()
        }
      }
      ad.show(activity)
    } else {
      loadInterstitialAd()
      onClosed()
    }
  }

  // ========================================================
  // 3. QUẢNG CÁO THƯỞNG (REWARDED AD)
  // ========================================================

  fun loadRewardedAd() {
    val adRequest = buildAdRequest()
    RewardedAd.load(
      context,
      rewardedAdUnitId,
      adRequest,
      object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
          rewardedAd = ad
          Log.d(TAG, "Rewarded Ad loaded successfully ($rewardedAdUnitId)")
        }
        override fun onAdFailedToLoad(error: LoadAdError) {
          rewardedAd = null
          Log.w(TAG, "Rewarded Ad load failed: ${error.message}")
        }
      }
    )
  }

  fun showRewardedAd(
    activity: Activity,
    onRewarded: () -> Unit,
    onFailedOrDismissed: () -> Unit
  ) {
    if (vipPrefs.isVip()) {
      Log.d(TAG, "Tài khoản VIP -> Tự động nhận thưởng không cần xem QC")
      onRewarded()
      return
    }

    val ad = rewardedAd
    if (ad != null) {
      var userEarnedReward = false
      ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          rewardedAd = null
          loadRewardedAd()
          if (userEarnedReward) {
            onRewarded()
          } else {
            onFailedOrDismissed()
          }
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          rewardedAd = null
          loadRewardedAd()
          onFailedOrDismissed()
        }
      }
      ad.show(activity) { rewardItem ->
        Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
        userEarnedReward = true
      }
    } else {
      loadRewardedAd()
      onFailedOrDismissed()
    }
  }

  fun pause() {
    bannerAdView?.pause()
  }

  fun resume() {
    bannerAdView?.resume()
  }

  fun destroy() {
    destroyBanner()
  }
}
