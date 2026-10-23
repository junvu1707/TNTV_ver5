package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.io.File
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.json.JSONObject

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
    // Các Test Ad Unit IDs chính thức từ Google AdMob (dành cho kiểm thử an toàn)
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
  }

  var webView: WebView? = null
  private var bannerAdView: AdView? = null
  private var interstitialAd: InterstitialAd? = null
  private var rewardedAd: RewardedAd? = null
  private var isAdMobInitialized = false
  private var bannerContainer: FrameLayout? = null

  // Trạng thái VIP, Google Play Billing & EmailJS Manager
  lateinit var vipPrefs: VipPreferenceManager
  lateinit var emailJSManager: EmailJSManager
  lateinit var billingManager: BillingManager

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Dọn dẹp variations_seed thiếu chữ ký/hỏng của Chromium nội bộ trước khi khởi tạo WebView
    try {
      val webViewDir = File(dataDir, "app_webview")
      val seedFile = File(webViewDir, "variations_seed")
      val seedNewFile = File(webViewDir, "variations_seed_new")
      if (seedFile.exists()) seedFile.delete()
      if (seedNewFile.exists()) seedNewFile.delete()
    } catch (_: Exception) {}

    // 1. Khởi tạo VIP Manager, EmailJS Manager & Google Play Billing
    vipPrefs = VipPreferenceManager(this)
    emailJSManager = EmailJSManager()
    billingManager = BillingManager(this, vipPrefs, emailJSManager)
    billingManager.onPurchaseSuccessCallback = { productId, level ->
      runOnUiThread {
        updateAdMobForVipStatus()
        val userName = vipPrefs.getUserName().ifBlank { "Tài khoản VIP" }
        webView?.evaluateJavascript(
          "if (window.onVipStatusChanged) { window.onVipStatusChanged($level, '$userName'); }",
          null
        )
        showVipSuccessDialog(productId, level)
      }
    }

    // 2. Cấu hình tuân thủ Google Play Families Policy (Chính sách gia đình & trẻ em)
    val requestConfiguration = RequestConfiguration.Builder()
      .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
      .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
      .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
      .build()
    MobileAds.setRequestConfiguration(requestConfiguration)

    // Khởi tạo Google AdMob SDK
    MobileAds.initialize(this) { status ->
      Log.d(TAG, "AdMob SDK Initialized: $status")
      isAdMobInitialized = true
      runOnUiThread {
        if (vipPrefs.shouldShowInterstitialAd()) {
          loadInterstitialAd()
        }
        loadRewardedAd()
      }
    }

    // 3. Giao diện chứa: WebView + Banner Ad ở chân màn hình
    val rootLayout = LinearLayout(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#E7F3D9"))
    }

    // Xử lý Edge-to-Edge System Bars
    ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
      val bars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      rootLayout.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    val wv = WebView(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
      )
      setBackgroundColor(Color.parseColor("#E7F3D9"))

      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        displayZoomControls = false
        builtInZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = true
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          safeBrowsingEnabled = false
        }
      }

      webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
          Log.d("WebViewConsole", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
          return true
        }

        override fun onJsAlert(
          view: WebView?,
          url: String?,
          message: String?,
          result: JsResult?
        ): Boolean {
          AlertDialog.Builder(this@MainActivity)
            .setTitle("Siêu Trạng Nhí Tiếng Việt")
            .setMessage(message ?: "")
            .setPositiveButton("Đồng ý") { _, _ -> result?.confirm() }
            .setOnCancelListener { result?.cancel() }
            .create()
            .show()
          return true
        }

        override fun onJsConfirm(
          view: WebView?,
          url: String?,
          message: String?,
          result: JsResult?
        ): Boolean {
          AlertDialog.Builder(this@MainActivity)
            .setTitle("Siêu Trạng Nhí Tiếng Việt")
            .setMessage(message ?: "")
            .setPositiveButton("Có") { _, _ -> result?.confirm() }
            .setNegativeButton("Không") { _, _ -> result?.cancel() }
            .setOnCancelListener { result?.cancel() }
            .create()
            .show()
          return true
        }
      }

      webViewClient = object : WebViewClient() {
        override fun onReceivedError(
          view: WebView?,
          request: WebResourceRequest?,
          error: WebResourceError?
        ) {
          super.onReceivedError(view, request, error)
          Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          Log.d(TAG, "WebView onPageFinished: $url")
        }
      }

      addJavascriptInterface(
        AndroidBridge(this@MainActivity, { this }),
        "AndroidBridge"
      )

      loadUrl("file:///android_asset/index.html")
    }
    webView = wv
    rootLayout.addView(wv)

    // Khung chứa Banner Ad ở chân màn hình
    val bContainer = FrameLayout(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      visibility = if (vipPrefs.shouldShowBannerAd()) View.VISIBLE else View.GONE
    }
    bannerContainer = bContainer
    rootLayout.addView(bContainer)

    setContentView(rootLayout)

    // Khởi tạo và nạp Banner Ad (nếu không phải gói VIP 2)
    if (vipPrefs.shouldShowBannerAd()) {
      initBannerAd()
    }

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (webView?.url?.contains("privacy.html") == true) {
          webView?.loadUrl("file:///android_asset/index.html")
          return
        }
        webView?.evaluateJavascript(
          "if (window.handleAndroidBack) { window.handleAndroidBack(); } else if (window.history.length > 1) { window.history.back(); } else { if (typeof AndroidBridge !== 'undefined' && AndroidBridge.exitApp) { AndroidBridge.exitApp(); } }",
          null
        )
      }
    })
  }

  // --- QUẢN LÝ QUẢNG CÁO THEO GÓI VIP ---

  private fun buildAdRequest(): AdRequest {
    val extras = Bundle().apply {
      putString("npa", "1")
    }
    return AdRequest.Builder()
      .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
      .build()
  }

  fun initBannerAd() {
    if (!vipPrefs.shouldShowBannerAd()) {
      bannerContainer?.visibility = View.GONE
      bannerAdView?.destroy()
      bannerAdView = null
      return
    }

    try {
      val adView = AdView(this).apply {
        setAdSize(AdSize.BANNER)
        adUnitId = TEST_BANNER_ID
        adListener = object : AdListener() {
          override fun onAdLoaded() {
            Log.d(TAG, "AdMob Banner loaded successfully")
            if (vipPrefs.shouldShowBannerAd()) {
              bannerContainer?.visibility = View.VISIBLE
            }
          }
          override fun onAdFailedToLoad(error: LoadAdError) {
            Log.w(TAG, "AdMob Banner failed to load: ${error.message}")
          }
        }
      }
      bannerAdView = adView
      bannerContainer?.removeAllViews()
      bannerContainer?.addView(adView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER
      ))
      val adRequest = buildAdRequest()
      adView.loadAd(adRequest)
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi tạo Banner Ad: ${e.message}")
    }
  }

  fun updateAdMobForVipStatus() {
    if (!vipPrefs.shouldShowBannerAd()) {
      bannerContainer?.visibility = View.GONE
      bannerAdView?.destroy()
      bannerAdView = null
    } else {
      if (bannerAdView == null) {
        initBannerAd()
      } else {
        bannerContainer?.visibility = View.VISIBLE
      }
    }
  }

  fun setBannerVisibility(visible: Boolean) {
    runOnUiThread {
      if (!vipPrefs.shouldShowBannerAd()) {
        bannerContainer?.visibility = View.GONE
      } else {
        bannerContainer?.visibility = if (visible) View.VISIBLE else View.GONE
      }
    }
  }

  fun loadInterstitialAd() {
    if (!vipPrefs.shouldShowInterstitialAd()) return

    val adRequest = buildAdRequest()
    InterstitialAd.load(
      this,
      TEST_INTERSTITIAL_ID,
      adRequest,
      object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
          interstitialAd = ad
          Log.d(TAG, "AdMob Interstitial loaded successfully")
        }
        override fun onAdFailedToLoad(error: LoadAdError) {
          interstitialAd = null
          Log.w(TAG, "AdMob Interstitial load failed: ${error.message}")
        }
      }
    )
  }

  fun showInterstitialAd(onClosed: () -> Unit) {
    runOnUiThread {
      // Bỏ qua hiển thị nếu VIP 2 (0%) hoặc VIP 1 không trúng xác suất 20%
      if (!vipPrefs.shouldShowInterstitialAd()) {
        Log.d(TAG, "Bỏ qua Interstitial Ad theo chính sách VIP Level ${vipPrefs.getVipLevel()}")
        onClosed()
        return@runOnUiThread
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
        ad.show(this)
      } else {
        loadInterstitialAd()
        onClosed()
      }
    }
  }

  fun loadRewardedAd() {
    val adRequest = buildAdRequest()
    RewardedAd.load(
      this,
      TEST_REWARDED_ID,
      adRequest,
      object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
          rewardedAd = ad
          Log.d(TAG, "AdMob Rewarded Video loaded successfully")
        }
        override fun onAdFailedToLoad(error: LoadAdError) {
          rewardedAd = null
          Log.w(TAG, "AdMob Rewarded Video load failed: ${error.message}")
        }
      }
    )
  }

  fun showRewardedAd(onRewarded: () -> Unit, onFailedOrDismissed: () -> Unit) {
    runOnUiThread {
      // Nếu là tài khoản VIP, tự động thưởng luôn mà không cần xem quảng cáo
      if (vipPrefs.isVip()) {
        Log.d(TAG, "Tài khoản VIP -> Tự động nhận thưởng không cần xem QC")
        onRewarded()
        return@runOnUiThread
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
        ad.show(this) { rewardItem ->
          Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
          userEarnedReward = true
        }
      } else {
        loadRewardedAd()
        onFailedOrDismissed()
      }
    }
  }

  fun showVipRegistrationDialog(productId: String, defaultName: String = "") {
    runOnUiThread {
      val isVip2Target = productId == "vip2" || productId == "vip_package_2"
      val vipTitleName = if (isVip2Target) "Gói VIP 2 (Trọn Đời - 99.000đ)" else "Gói VIP 1 (Mùa Thi - 49.000đ)"
      val deviceId = vipPrefs.getDeviceId()
      val savedEmail = vipPrefs.getUserEmail()
      val savedName = if (defaultName.isNotBlank()) defaultName else vipPrefs.getUserName()

      val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 36, 48, 24)
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
          setColor(Color.parseColor("#FFFBEB"))
          setStroke(6, Color.parseColor("#F59E0B"))
          cornerRadius = 36f
        }
        background = bgDrawable
      }

      val titleHeader = android.widget.TextView(this).apply {
        text = "👑 KÍCH HOẠT TÀI KHOẢN VIP"
        setTextColor(Color.parseColor("#991B1B"))
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, 8)
      }
      container.addView(titleHeader)

      val subTitle = android.widget.TextView(this).apply {
        text = vipTitleName
        setTextColor(Color.parseColor("#D97706"))
        textSize = 12f
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, 20)
      }
      container.addView(subTitle)

      // Dòng 1: Tài khoản : [input]
      val rowAccount = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = android.graphics.drawable.GradientDrawable().apply {
          setColor(Color.WHITE)
          setStroke(3, Color.parseColor("#FDE68A"))
          cornerRadius = 20f
        }
        background = rowBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 14) }
      }

      val lblAccount = android.widget.TextView(this).apply {
        text = "Tài khoản :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val inputAccount = android.widget.EditText(this).apply {
        hint = "Nhập tên tài khoản..."
        setText(if (savedName.isNotBlank() && savedName != "Công chúa tóc mây") savedName else "Tài khoản VIP")
        setSingleLine(true)
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        background = null
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowAccount.addView(lblAccount)
      rowAccount.addView(inputAccount)
      container.addView(rowAccount)

      // Dòng 2: Email : [input]
      val rowEmail = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = android.graphics.drawable.GradientDrawable().apply {
          setColor(Color.WHITE)
          setStroke(3, Color.parseColor("#FDE68A"))
          cornerRadius = 20f
        }
        background = rowBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 14) }
      }

      val lblEmail = android.widget.TextView(this).apply {
        text = "Email :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val inputEmail = android.widget.EditText(this).apply {
        hint = "phuhuynh@gmail.com"
        setText(savedEmail)
        inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        setSingleLine(true)
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        background = null
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowEmail.addView(lblEmail)
      rowEmail.addView(inputEmail)
      container.addView(rowEmail)

      // Dòng 3: ID : [1nabj]
      val rowDevice = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = android.graphics.drawable.GradientDrawable().apply {
          setColor(Color.parseColor("#FEF3C7"))
          setStroke(3, Color.parseColor("#F59E0B"))
          cornerRadius = 20f
        }
        background = rowBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      }

      val lblDevice = android.widget.TextView(this).apply {
        text = "ID :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val textDevice = android.widget.TextView(this).apply {
        text = deviceId
        setTextColor(Color.parseColor("#991B1B"))
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowDevice.addView(lblDevice)
      rowDevice.addView(textDevice)
      container.addView(rowDevice)

      val dialog = AlertDialog.Builder(this)
        .setView(container)
        .setPositiveButton("Thanh toán Google Play") { _, _ ->
          val inputName = inputAccount.text.toString().trim()
          val inputEmailStr = inputEmail.text.toString().trim()

          if (inputName.isNotBlank()) vipPrefs.setUserName(inputName)
          if (inputEmailStr.isNotBlank()) vipPrefs.setUserEmail(inputEmailStr)

          billingManager.launchBillingFlow(this, productId, inputEmailStr, inputName)
        }
        .setNeutralButton("🔍 Quét & Khôi Phục VIP") { _, _ ->
          val inputName = inputAccount.text.toString().trim()
          val inputEmailStr = inputEmail.text.toString().trim()
          billingManager.scanAndRestoreVip(inputName, inputEmailStr) { success, vipLevel, message ->
            runOnUiThread {
              if (success) {
                updateAdMobForVipStatus()
                val activeName = inputName.ifBlank { vipPrefs.getUserName().ifBlank { "Tài khoản VIP" } }
                vipPrefs.setUserName(activeName)
                if (inputEmailStr.isNotBlank()) vipPrefs.setUserEmail(inputEmailStr)

                webView?.evaluateJavascript(
                  "if (window.onVipStatusChanged) { window.onVipStatusChanged($vipLevel, '$activeName'); }",
                  null
                )
                Toast.makeText(this, "🎉 $message", Toast.LENGTH_LONG).show()
              } else {
                Toast.makeText(this, "🔍 $message", Toast.LENGTH_LONG).show()
              }
            }
          }
        }
        .setNegativeButton("Hủy", null)
        .create()

      dialog.show()

      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
        setTextColor(Color.parseColor("#059669"))
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
        setTextColor(Color.parseColor("#D97706"))
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
        setTextColor(Color.parseColor("#DC2626"))
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
    }
  }

  fun showVipSuccessDialog(productId: String, vipLevel: Int) {

    runOnUiThread {
      val vipName = if (vipLevel >= 2) "VIP 2 (Trọn Đời)" else "VIP 1 (Mùa Thi)"
      val email = vipPrefs.getUserEmail()
      val name = vipPrefs.getUserName()
      val deviceId = vipPrefs.getDeviceId()

      AlertDialog.Builder(this)
        .setTitle("Kích Hoạt VIP Thành Công 🎉")
        .setMessage(
          "Chúc mừng ${if (name.isNotBlank()) name else "bé"} đã nâng cấp thành công gói $vipName!\n\n" +
          "• Tài khoản: ${if (name.isNotBlank()) name else "Học sinh"}\n" +
          "• Email xác nhận: ${if (email.isNotBlank()) email else "Đã ghi nhận"}\n" +
          "• Mã thiết bị: $deviceId\n" +
          "• Trạng thái: Toàn bộ 18 vòng thi và tính năng VIP đã sẵn sàng.\n\n" +
          "Thông tin kích hoạt đã được gửi tự động về email quản trị (junvu1707@gmail.com)."
        )
        .setPositiveButton("Bắt đầu học ngay") { dialog, _ ->
          dialog.dismiss()
        }
        .setCancelable(false)
        .show()
    }
  }

  override fun onPause() {
    bannerAdView?.pause()
    webView?.onPause()
    webView?.pauseTimers()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    bannerAdView?.resume()
    webView?.onResume()
    webView?.resumeTimers()
    
    // Đồng bộ trạng thái khi mở lại ứng dụng
    updateAdMobForVipStatus()
  }

  override fun onDestroy() {
    try {
      billingManager.destroy()
    } catch (_: Exception) {}
    bannerAdView?.destroy()
    bannerAdView = null
    webView?.destroy()
    webView = null
    super.onDestroy()
  }
}

/**
 * Android Native Bridge kết nối giữa JavaScript (WebView) và Kotlin Native
 */
class AndroidBridge(
  private val activity: MainActivity,
  private val webViewProvider: () -> WebView?
) {

  /**
   * Kích hoạt luồng thanh toán Google Play Billing từ JavaScript
   */
  @JavascriptInterface
  fun launchBillingFlow(productId: String) {
    activity.showVipRegistrationDialog(productId, activity.vipPrefs.getUserName())
  }

  @JavascriptInterface
  fun launchBillingFlowWithInfo(productId: String, userEmail: String, userName: String) {
    activity.runOnUiThread {
      activity.billingManager.launchBillingFlow(activity, productId, userEmail, userName)
    }
  }

  @JavascriptInterface
  fun openVipPurchaseDialog(productId: String, defaultName: String) {
    activity.showVipRegistrationDialog(productId, defaultName)
  }

  /**
   * Gửi yêu cầu kích hoạt VIP qua chuyển khoản VietQR & EmailJS
   */
  @JavascriptInterface
  fun sendVipTransferRequest(productId: String, userEmail: String) {
    activity.runOnUiThread {
      if (userEmail.isNotBlank()) {
        activity.vipPrefs.setUserEmail(userEmail)
      }
      val level = if (productId == "vip2" || productId == "vip_package_2") VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
      val orderId = "VIETQR_" + System.currentTimeMillis()
      activity.vipPrefs.setLastOrderId(orderId)
      activity.vipPrefs.setVipLevel(level)

      // Gửi xác nhận qua EmailJS
      activity.emailJSManager.sendOrderConfirmation(userEmail, orderId, level) { success ->
        Log.d("AndroidBridge", "VietQR & EmailJS request sent: $success")
      }

      activity.updateAdMobForVipStatus()
      webViewProvider()?.evaluateJavascript(
        "if (window.onVipStatusChanged) { window.onVipStatusChanged($level); }",
        null
      )
    }
  }

  @JavascriptInterface
  fun restorePurchases() {
    activity.runOnUiThread {
      activity.billingManager.queryPurchases()
      Toast.makeText(activity, "Đã kiểm tra và đồng bộ trạng thái VIP thành công!", Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun scanAndRestoreVip(userName: String, userEmail: String) {
    activity.runOnUiThread {
      activity.billingManager.scanAndRestoreVip(userName, userEmail) { success, vipLevel, message ->
        activity.runOnUiThread {
          if (success) {
            activity.updateAdMobForVipStatus()
            val activeName = userName.ifBlank { activity.vipPrefs.getUserName().ifBlank { "Tài khoản VIP" } }
            activity.vipPrefs.setUserName(activeName)
            if (userEmail.isNotBlank()) activity.vipPrefs.setUserEmail(userEmail)

            activity.webView?.evaluateJavascript(
              "if (window.onVipStatusChanged) { window.onVipStatusChanged($vipLevel, '$activeName'); }",
              null
            )
            Toast.makeText(activity, "🎉 $message", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(activity, "🔍 $message", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }


  /**
   * Trả về cấp độ VIP hiện tại (0: Free, 1: VIP 1, 2: VIP 2)
   */
  @JavascriptInterface
  fun getVipLevel(): Int {
    return activity.vipPrefs.getVipLevel()
  }

  @JavascriptInterface
  fun isVip(): Boolean {
    return activity.vipPrefs.isVip()
  }

  @JavascriptInterface
  fun isVip2(): Boolean {
    return activity.vipPrefs.isVip2()
  }

  /**
   * Lấy Unique Device ID (Settings.Secure.ANDROID_ID)
   */
  @JavascriptInterface
  fun getDeviceId(): String {
    return activity.vipPrefs.getDeviceId()
  }

  @JavascriptInterface
  fun getUserEmail(): String {
    return activity.vipPrefs.getUserEmail()
  }

  @JavascriptInterface
  fun getUserName(): String {
    return activity.vipPrefs.getUserName()
  }

  /**
   * Kiểm tra xem vòng thi (1-18) có mở khóa hay không
   */
  @JavascriptInterface
  fun isRoundUnlocked(roundIndex: Int): Boolean {
    return activity.vipPrefs.isRoundUnlocked(roundIndex)
  }

  /**
   * Trả về toàn bộ thông tin VIP ở dạng JSON cho JavaScript
   */
  @JavascriptInterface
  fun getVipInfoJson(): String {
    val json = JSONObject().apply {
      put("vipLevel", activity.vipPrefs.getVipLevel())
      put("isVip", activity.vipPrefs.isVip())
      put("isVip1", activity.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_1)
      put("isVip2", activity.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_2)
      put("deviceId", activity.vipPrefs.getDeviceId())
      put("userName", activity.vipPrefs.getUserName())
      put("userEmail", activity.vipPrefs.getUserEmail())
      put("lastOrderId", activity.vipPrefs.getLastOrderId())
    }
    return json.toString()
  }

  @JavascriptInterface
  fun requestRewardedAd(rewardType: String, targetId: String) {
    activity.runOnUiThread {
      activity.showRewardedAd(
        onRewarded = {
          activity.runOnUiThread {
            webViewProvider()?.evaluateJavascript(
              "if (window.onRewardedAdSuccess) { window.onRewardedAdSuccess('$rewardType', '$targetId'); }",
              null
            )
          }
        },
        onFailedOrDismissed = {
          activity.runOnUiThread {
            webViewProvider()?.evaluateJavascript(
              "if (window.onRewardedAdFallback) { window.onRewardedAdFallback('$rewardType', '$targetId'); }",
              null
            )
          }
        }
      )
    }
  }

  @JavascriptInterface
  fun requestRewardedAd(rewardType: String, targetId: Int) {
    requestRewardedAd(rewardType, targetId.toString())
  }

  @JavascriptInterface
  fun requestRewardedAd(roundIndex: Int) {
    requestRewardedAd("round", roundIndex.toString())
  }

  @JavascriptInterface
  fun requestInterstitialAd() {
    activity.runOnUiThread {
      activity.showInterstitialAd {
        activity.runOnUiThread {
          webViewProvider()?.evaluateJavascript(
            "if (window.onInterstitialAdClosed) { window.onInterstitialAdClosed(); }",
            null
          )
        }
      }
    }
  }

  @JavascriptInterface
  fun setBannerVisible(visible: Boolean) {
    activity.setBannerVisibility(visible)
  }

  @JavascriptInterface
  fun showToast(message: String) {
    activity.runOnUiThread {
      Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun exitApp() {
    activity.runOnUiThread {
      activity.finish()
    }
  }

  @JavascriptInterface
  fun openBrowserUrl(url: String) {
    activity.runOnUiThread {
      try {
        val targetUri = if (url.startsWith("http://") || url.startsWith("https://")) {
          Uri.parse(url)
        } else if (url == "privacy.html" || url.endsWith("/privacy.html")) {
          Uri.parse("https://ais-pre-26bhyqdchn5lcuuybrdybh-53627996710.asia-southeast1.run.app/privacy.html")
        } else {
          Uri.parse(url)
        }
        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
      } catch (e: Exception) {
        webViewProvider()?.loadUrl("file:///android_asset/privacy.html")
      }
    }
  }
}
