package com.vttd.sieutrangnhi

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
  }

  var webView: WebView? = null
  private var bannerContainer: FrameLayout? = null

  lateinit var vipPrefs: VipPreferenceManager
  lateinit var emailJSManager: EmailJSManager
  lateinit var billingManager: BillingManager
  lateinit var githubRepo: GitHubQuestionRepository
  lateinit var examGenerator: DynamicExamGenerator
  lateinit var adManager: AdManager
  lateinit var chibiManager: ChibiManager
  lateinit var vipDialogManager: VipDialogManager

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

    // 1. Khởi tạo Manager, GitHub Repo, Exam Generator, AdManager & Billing
    chibiManager = ChibiManager(this)
    githubRepo = GitHubQuestionRepository(this)
    githubRepo.syncAllGradesInBackground()
    examGenerator = DynamicExamGenerator(githubRepo)

    vipPrefs = VipPreferenceManager(this)
    val smartSyncManager = SmartExamSyncManager(this, vipPrefs)
    smartSyncManager.performSmartSync { updatedCount ->
      Log.d("MainActivity", "[SmartSync] Tự động đồng bộ hoàn tất: $updatedCount file bộ đề thi được cập nhật.")
    }

    adManager = AdManager(this, vipPrefs)
    vipDialogManager = VipDialogManager(this)

    emailJSManager = EmailJSManager()
    billingManager = BillingManager(this, vipPrefs, emailJSManager)
    billingManager.onPurchaseSuccessCallback = { productId, level ->
      runOnUiThread {
        updateAdMobForVipStatus()
        val userName = vipPrefs.getUserName().ifBlank { "Tài khoản VIP" }.replace("'", "\\'")
        webView?.evaluateJavascript(
          "if (window.onVipStatusChanged) { window.onVipStatusChanged($level, '$userName'); }",
          null
        )
        showVipSuccessDialog(productId, level)
      }
    }

    // 2. Giao diện chứa: WebView + Banner Ad ở chân màn hình
    val rootLayout = LinearLayout(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#F8FCF5"))
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
      setBackgroundColor(Color.parseColor("#F8FCF5"))

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
        textZoom = 100
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
          val level = vipPrefs.getVipLevel()
          val userName = vipPrefs.getUserName().replace("'", "\\'")
          view?.evaluateJavascript(
            "if (window.onVipStatusChanged) { window.onVipStatusChanged($level, '$userName', true); }",
            null
          )
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

  // --- QUẢN LÝ QUẢNG CÁO VIA ADMANAGER ---

  fun initBannerAd() {
    bannerContainer?.let { adManager.initBannerAd(it) }
  }

  fun updateAdMobForVipStatus() {
    adManager.updateBannerVisibility(bannerContainer, true)
  }

  fun setBannerVisibility(visible: Boolean) {
    runOnUiThread {
      adManager.updateBannerVisibility(bannerContainer, visible)
    }
  }

  fun loadInterstitialAd() {
    adManager.loadInterstitialAd()
  }

  fun showInterstitialAd(onClosed: () -> Unit) {
    runOnUiThread {
      adManager.showInterstitialAd(this, onClosed)
    }
  }

  fun loadRewardedAd() {
    adManager.loadRewardedAd()
  }

  fun showRewardedAd(onRewarded: () -> Unit, onFailedOrDismissed: () -> Unit) {
    runOnUiThread {
      adManager.showRewardedAd(this, onRewarded, onFailedOrDismissed)
    }
  }

  // --- QUẢN LÝ DIALOG VIA VIPDIALOGMANAGER ---

  fun showParentalGateDialog(onPassed: () -> Unit) {
    vipDialogManager.showParentalGateDialog(onPassed)
  }

  fun showVipRegistrationDialog(productId: String, defaultName: String = "") {
    vipDialogManager.showVipRegistrationDialog(productId, defaultName)
  }

  fun showVipSuccessDialog(productId: String, vipLevel: Int) {
    vipDialogManager.showVipSuccessDialog(productId, vipLevel)
  }

  override fun onPause() {
    adManager.pause()
    try {
      webView?.evaluateJavascript(
        "if (window.SoundEngine && typeof window.SoundEngine.suspend === 'function') { window.SoundEngine.suspend(); }",
        null
      )
    } catch (_: Exception) {}
    webView?.onPause()
    webView?.pauseTimers()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    adManager.resume()
    webView?.onResume()
    webView?.resumeTimers()
    try {
      webView?.evaluateJavascript(
        "if (window.SoundEngine && typeof window.SoundEngine.resume === 'function') { window.SoundEngine.resume(); }",
        null
      )
    } catch (_: Exception) {}

    // Đồng bộ trạng thái khi mở lại ứng dụng
    updateAdMobForVipStatus()
  }

  override fun onDestroy() {
    try {
      billingManager.destroy()
    } catch (_: Exception) {}
    adManager.destroy()

    bannerContainer?.removeAllViews()
    bannerContainer = null

    val wv: WebView? = webView
    if (wv != null) {
      wv.stopLoading()
      wv.removeJavascriptInterface("AndroidBridge")
      wv.webChromeClient = null
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.removeAllViews()
      wv.destroy()
    }
    webView = null
    super.onDestroy()
  }
}
