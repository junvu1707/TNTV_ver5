package com.vttd.sieutrangnhi

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference

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

    // 1. Khởi tạo VIP Manager, Chibi Manager, GitHub Repo, Exam Generator, AdManager & Billing
    chibiManager = ChibiManager(this)
    githubRepo = GitHubQuestionRepository(this)
    githubRepo.syncAllGradesInBackground()
    examGenerator = DynamicExamGenerator(githubRepo)

    vipPrefs = VipPreferenceManager(this)
    adManager = AdManager(this, vipPrefs)

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

  fun showVipRegistrationDialog(productId: String, defaultName: String = "") {
    if (isFinishing() || isDestroyed()) return
    runOnUiThread {
      if (isFinishing() || isDestroyed()) return@runOnUiThread
      val isVip2Target = productId == BillingManager.PRODUCT_VIP_2
      val vipTitleName = if (isVip2Target) "Gói VIP 2 (Trọn Đời - 99.000đ)" else "Gói VIP 1 (Mùa Thi - 49.000đ)"
      val deviceId = vipPrefs.getDeviceId()
      val savedEmail = vipPrefs.getUserEmail()
      val savedName = if (defaultName.isNotBlank()) defaultName else vipPrefs.getUserName()

      val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(52, 40, 52, 32)
        val bgDrawable = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFBEB"))
          setStroke(8, Color.parseColor("#F59E0B"))
          cornerRadius = 40f
        }
        background = bgDrawable
      }

      val titleHeader = TextView(this).apply {
        text = "👑 KÍCH HOẠT TÀI KHOẢN VIP"
        setTextColor(Color.parseColor("#991B1B"))
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 8)
      }
      container.addView(titleHeader)

      val subTitle = TextView(this).apply {
        text = vipTitleName
        setTextColor(Color.parseColor("#D97706"))
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 20)
      }
      container.addView(subTitle)

      // Dòng 1: Tài khoản : [input]
      val rowAccount = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = GradientDrawable().apply {
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

      val lblAccount = TextView(this).apply {
        text = "Tài khoản :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val inputAccount = EditText(this).apply {
        hint = "Nhập tên tài khoản..."
        setText(if (savedName.isNotBlank() && savedName != "Siêu Trạng Nhí") savedName else "Tài khoản VIP")
        setSingleLine(true)
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        background = null
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowAccount.addView(lblAccount)
      rowAccount.addView(inputAccount)
      container.addView(rowAccount)

      // Dòng 2: Email : [input]
      val rowEmail = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = GradientDrawable().apply {
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

      val lblEmail = TextView(this).apply {
        text = "Email :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val inputEmail = EditText(this).apply {
        hint = "phuhuynh@gmail.com"
        setText(savedEmail)
        inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        setSingleLine(true)
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        background = null
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowEmail.addView(lblEmail)
      rowEmail.addView(inputEmail)
      container.addView(rowEmail)

      // Dòng 3: ID : [1nabj]
      val rowDevice = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 14, 24, 14)
        val rowBg = GradientDrawable().apply {
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

      val lblDevice = TextView(this).apply {
        text = "ID :"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 12, 0)
      }

      val textDevice = TextView(this).apply {
        text = deviceId
        setTextColor(Color.parseColor("#991B1B"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      rowDevice.addView(lblDevice)
      rowDevice.addView(textDevice)
      container.addView(rowDevice)

      var dialogRef: AlertDialog? = null

      // Dòng 4: Nút Thanh toán Google Play
      val btnPay = Button(this).apply {
        text = "Thanh toán Google Play"
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        val btnBg = GradientDrawable().apply {
          setColor(Color.parseColor("#059669"))
          cornerRadius = 24f
        }
        background = btnBg
        setPadding(0, 20, 0, 20)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 20, 0, 12) }

        setOnClickListener {
          val inputName = inputAccount.text.toString().trim()
          val inputEmailStr = inputEmail.text.toString().trim()

          if (inputName.isNotBlank()) vipPrefs.setUserName(inputName)
          if (inputEmailStr.isNotBlank()) vipPrefs.setUserEmail(inputEmailStr)

          dialogRef?.dismiss()
          billingManager.launchBillingFlow(this@MainActivity, productId, inputEmailStr, inputName)
        }
      }
      container.addView(btnPay)

      // Dòng 5: Nút Khôi phục VIP
      val btnRestore = Button(this).apply {
        text = "Khôi phục VIP"
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        val btnBg = GradientDrawable().apply {
          setColor(Color.parseColor("#D97706"))
          cornerRadius = 24f
        }
        background = btnBg
        setPadding(0, 20, 0, 20)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 12) }

        setOnClickListener {
          val inputName = inputAccount.text.toString().trim()
          val inputEmailStr = inputEmail.text.toString().trim()
          dialogRef?.dismiss()
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
                Toast.makeText(this@MainActivity, "🎉 $message", Toast.LENGTH_LONG).show()
              } else {
                Toast.makeText(this@MainActivity, "🔍 $message", Toast.LENGTH_LONG).show()
              }
            }
          }
        }
      }
      container.addView(btnRestore)

      // Dòng 6: Nút Hủy
      val btnCancel = TextView(this).apply {
        text = "Hủy"
        setTextColor(Color.parseColor("#DC2626"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 12, 0, 4)
        isClickable = true
        isFocusable = true
        setOnClickListener {
          dialogRef?.dismiss()
        }
      }
      container.addView(btnCancel)

      dialogRef = AlertDialog.Builder(this)
        .setView(container)
        .create()

      dialogRef.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
      }
      dialogRef.show()
    }
  }

  fun showVipSuccessDialog(productId: String, vipLevel: Int) {
    if (isFinishing || isDestroyed) return
    runOnUiThread {
      if (isFinishing || isDestroyed) return@runOnUiThread
      val vipName = if (vipLevel >= 2) "VIP 2 (Trọn Đời)" else "VIP 1 (Mùa Thi)"
      val email = vipPrefs.getUserEmail().ifBlank { "Đã ghi nhận" }
      val name = vipPrefs.getUserName().ifBlank { "Học sinh" }
      val deviceId = vipPrefs.getDeviceId()

      var dialogRef: AlertDialog? = null

      val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 44, 48, 44)
        val bgDrawable = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFDF7"))
          cornerRadius = 48f
          setStroke(6, Color.parseColor("#F59E0B"))
        }
        background = bgDrawable
        elevation = 20f
      }

      // Icon Header
      val headerIcon = TextView(this).apply {
        text = "👑"
        textSize = 40f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 8) }
      }
      container.addView(headerIcon)

      // Title
      val txtTitle = TextView(this).apply {
        text = "KÍCH HOẠT VIP THÀNH CÔNG 🎉"
        setTextColor(Color.parseColor("#92400E"))
        textSize = 17f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 10) }
      }
      container.addView(txtTitle)

      // Subtitle Greeting
      val txtGreeting = TextView(this).apply {
        text = "Chúc mừng $name đã nâng cấp thành công gói $vipName!"
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setLineSpacing(6f, 1f)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 18) }
      }
      container.addView(txtGreeting)

      // Khung thông tin chi tiết
      val infoBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 24, 32, 24)
        val boxBg = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFBEB"))
          cornerRadius = 28f
          setStroke(3, Color.parseColor("#FDE68A"))
        }
        background = boxBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }
      }

      val rows = listOf(
        "👤 Tài khoản:" to name,
        "✉️ Email xác nhận:" to email,
        "📱 Mã thiết bị:" to deviceId,
        "⭐ Trạng thái:" to "Mở khóa toàn bộ 18 vòng & VIP"
      )

      rows.forEach { (label, value) ->
        val row = LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 4, 0, 4) }
        }
        val lbl = TextView(this).apply {
          text = label
          setTextColor(Color.parseColor("#78350F"))
          textSize = 12f
          setTypeface(null, Typeface.BOLD)
          setPadding(0, 0, 8, 0)
        }
        val valTxt = TextView(this).apply {
          text = value
          setTextColor(if (label.contains("Mã thiết bị")) Color.parseColor("#B91C1C") else Color.parseColor("#0F172A"))
          textSize = 12f
          setTypeface(null, Typeface.BOLD)
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(lbl)
        row.addView(valTxt)
        infoBox.addView(row)
      }
      container.addView(infoBox)

      // Notice
      val txtNotice = TextView(this).apply {
        text = "Thông tin kích hoạt đã được đồng bộ tự động."
        setTextColor(Color.parseColor("#B45309"))
        textSize = 11f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 18) }
      }
      container.addView(txtNotice)

      // Button Bắt đầu học ngay
      val btnSubmit = Button(this).apply {
        text = "Bắt đầu học ngay 🚀"
        setTextColor(Color.WHITE)
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        val btnBg = GradientDrawable().apply {
          orientation = GradientDrawable.Orientation.TOP_BOTTOM
          colors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669"))
          cornerRadius = 28f
        }
        background = btnBg
        setPadding(0, 22, 0, 22)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener {
          dialogRef?.dismiss()
        }
      }
      container.addView(btnSubmit)

      dialogRef = AlertDialog.Builder(this)
        .setView(container)
        .setCancelable(false)
        .create()

      dialogRef?.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      }
      dialogRef?.show()
    }
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

/**
 * Android Native Bridge kết nối giữa JavaScript (WebView) và Kotlin Native
 */
class AndroidBridge(
  activity: MainActivity,
  private val webViewProvider: () -> WebView?
) {
  private val activityRef = WeakReference(activity)
  private val activity: MainActivity? get() = activityRef.get()

  /**
   * Kích hoạt luồng thanh toán Google Play Billing từ JavaScript
   */
  @JavascriptInterface
  fun launchBillingFlow(productId: String) {
    val act = activity ?: return
    act.showVipRegistrationDialog(productId, act.vipPrefs.getUserName())
  }

  @JavascriptInterface
  fun launchBillingFlowWithInfo(productId: String, userEmail: String, userName: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.billingManager.launchBillingFlow(act, productId, userEmail, userName)
    }
  }

  @JavascriptInterface
  fun openVipPurchaseDialog(productId: String, defaultName: String) {
    val act = activity ?: return
    act.showVipRegistrationDialog(productId, defaultName)
  }



  @JavascriptInterface
  fun restorePurchases() {
    val act = activity ?: return
    act.runOnUiThread {
      act.billingManager.queryPurchases()
      Toast.makeText(act, "Đã kiểm tra và đồng bộ trạng thái VIP thành công!", Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun scanAndRestoreVip(userName: String, userEmail: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.billingManager.scanAndRestoreVip(userName, userEmail) { success, vipLevel, message ->
        act.runOnUiThread {
          if (success) {
            act.updateAdMobForVipStatus()
            val activeName = userName.ifBlank { act.vipPrefs.getUserName().ifBlank { "Tài khoản VIP" } }
            act.vipPrefs.setUserName(activeName)
            if (userEmail.isNotBlank()) act.vipPrefs.setUserEmail(userEmail)

            act.webView?.evaluateJavascript(
              "if (window.onVipStatusChanged) { window.onVipStatusChanged($vipLevel, '$activeName'); }",
              null
            )
            Toast.makeText(act, "🎉 $message", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(act, "🔍 $message", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  /**
   * Lấy đường dẫn cục bộ (Local URI file://) của ảnh Chibi từ GitHub/Cache (Offline-First kèm Fallback)
   */
  @JavascriptInterface
  fun getChibiLocalPath(fileName: String): String {
    val act = activity ?: return "file:///android_asset/app_logo.png"
    return kotlinx.coroutines.runBlocking {
      act.chibiManager.getChibiUriForWebView(fileName)
    }
  }

  /**
   * Trả về cấp độ VIP hiện tại (0: Free, 1: VIP 1, 2: VIP 2)
   */
  @JavascriptInterface
  fun getVipLevel(): Int {
    return activity?.vipPrefs?.getVipLevel() ?: VipPreferenceManager.VIP_LEVEL_0
  }

  @JavascriptInterface
  fun isVip(): Boolean {
    return activity?.vipPrefs?.isVip() ?: false
  }

  @JavascriptInterface
  fun isVip2(): Boolean {
    return activity?.vipPrefs?.isVip2() ?: false
  }

  /**
   * Lấy Unique Device ID (Settings.Secure.ANDROID_ID)
   */
  @JavascriptInterface
  fun getDeviceId(): String {
    return activity?.vipPrefs?.getDeviceId() ?: ""
  }

  @JavascriptInterface
  fun getUserEmail(): String {
    return activity?.vipPrefs?.getUserEmail() ?: ""
  }

  @JavascriptInterface
  fun getUserName(): String {
    return activity?.vipPrefs?.getUserName() ?: ""
  }

  /**
   * Kiểm tra xem vòng thi (1-18) có mở khóa hay không
   */
  @JavascriptInterface
  fun isRoundUnlocked(roundIndex: Int): Boolean {
    return activity?.vipPrefs?.isRoundUnlocked(roundIndex) ?: (roundIndex <= 6)
  }

  /**
   * Trả về toàn bộ thông tin VIP ở dạng JSON cho JavaScript
   */
  @JavascriptInterface
  fun getVipInfoJson(): String {
    val act = activity ?: return "{}"
    val json = JSONObject().apply {
      put("vipLevel", act.vipPrefs.getVipLevel())
      put("isVip", act.vipPrefs.isVip())
      put("isVip1", act.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_1)
      put("isVip2", act.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_2)
      put("deviceId", act.vipPrefs.getDeviceId())
      put("userName", act.vipPrefs.getUserName())
      put("userEmail", act.vipPrefs.getUserEmail())
      put("lastOrderId", act.vipPrefs.getLastOrderId())
    }
    return json.toString()
  }

  @JavascriptInterface
  fun requestRewardedAd(rewardType: String, targetId: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.showRewardedAd(
        onRewarded = {
          act.runOnUiThread {
            webViewProvider()?.evaluateJavascript(
              "if (window.onRewardedAdSuccess) { window.onRewardedAdSuccess('$rewardType', '$targetId'); }",
              null
            )
          }
        },
        onFailedOrDismissed = {
          act.runOnUiThread {
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
    val act = activity ?: return
    act.runOnUiThread {
      act.showInterstitialAd {
        act.runOnUiThread {
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
    activity?.setBannerVisibility(visible)
  }

  @JavascriptInterface
  fun generateDynamicExam(grade: Int, changOrRound: Int, seed: Int, fallbackJson: String = ""): String {
    val act = activity ?: return ""
    return act.examGenerator.generateDynamicMode2Exam(
      grade = grade,
      changOrRound = changOrRound,
      seed = seed,
      localQuestionsJsonStr = fallbackJson,
      targetCount = 30
    )
  }

  @JavascriptInterface
  fun generateDynamicMode2Exam(grade: Int, changOrRound: Int, seed: Int, fallbackJson: String = ""): String {
    return generateDynamicExam(grade, changOrRound, seed, fallbackJson)
  }

  @JavascriptInterface
  fun getGithubQuestionsJson(grade: Int, changOrRound: Int): String {
    return activity?.githubRepo?.getCachedQuestionsJson(grade, changOrRound) ?: ""
  }

  @JavascriptInterface
  fun getLocalAssetQuestionsJson(grade: Int, changOrRound: Int): String {
    return activity?.githubRepo?.getLocalAssetQuestionsJson(grade, changOrRound) ?: ""
  }

  @JavascriptInterface
  fun getDirectRawUrl(grade: Int, changOrRound: Int): String {
    return GitHubQuestionRepository.getDirectRawUrl(grade, changOrRound)
  }

  @JavascriptInterface
  fun fetchGithubQuestions(grade: Int, changOrRound: Int) {
    val act = activity ?: return
    act.githubRepo.fetchAndCacheQuestions(grade, changOrRound, onSuccess = {
      act.runOnUiThread {
        webViewProvider()?.evaluateJavascript(
          "if (window.onGithubQuestionsUpdated) { window.onGithubQuestionsUpdated($grade, $changOrRound); }",
          null
        )
      }
    })
  }

  @JavascriptInterface
  fun syncAllGithubQuestions(force: Boolean = true) {
    val act = activity ?: return
    act.githubRepo.syncRepoQuestions(forceRefresh = force) { success, message ->
      act.runOnUiThread {
        val safeMsg = message.replace("'", "\\'").replace("\n", " ")
        webViewProvider()?.evaluateJavascript(
          "if (window.onGithubSyncCompleted) { window.onGithubSyncCompleted($success, '$safeMsg'); }",
          null
        )
      }
    }
  }

  @JavascriptInterface
  fun showToast(message: String) {
    val act = activity ?: return
    act.runOnUiThread {
      Toast.makeText(act, message, Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun exitApp() {
    val act = activity ?: return
    act.runOnUiThread {
      act.finish()
    }
  }

  @JavascriptInterface
  fun openBrowserUrl(url: String) {
    val act = activity ?: return
    act.runOnUiThread {
      try {
        val targetUri = if (url == "privacy.html" || url.endsWith("/privacy.html")) {
          Uri.parse("https://sites.google.com/view/sieutrangnhi-tiengviet/trang-ch%E1%BB%A7")
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
          Uri.parse(url)
        } else {
          Uri.parse(url)
        }
        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        act.startActivity(intent)
      } catch (e: Exception) {
        act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/sieutrangnhi-tiengviet/trang-ch%E1%BB%A7")))
      }
    }
  }
}
