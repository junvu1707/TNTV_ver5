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

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
  }

  var webView: WebView? = null
  private var bannerContainer: FrameLayout? = null

  // Trạng thái VIP, Billing, EmailJS, GitHub Questions Repo & AdManager
  lateinit var vipPrefs: VipPreferenceManager
  lateinit var emailJSManager: EmailJSManager
  lateinit var billingManager: BillingManager
  lateinit var githubRepo: GitHubQuestionRepository
  lateinit var adManager: AdManager

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

    // 1. Khởi tạo VIP Manager, GitHub Repo, AdManager & Billing
    githubRepo = GitHubQuestionRepository(this)
    githubRepo.syncAllGradesInBackground()

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
    runOnUiThread {
      val isVip2Target = productId == "vip2" || productId == "vip_package_2"
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
        setText(if (savedName.isNotBlank() && savedName != "Công chúa tóc mây") savedName else "Tài khoản VIP")
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
          "Thông tin kích hoạt đã được gửi tự động về email quản trị (junvu.dev@gmail.com)."
        )
        .setPositiveButton("Bắt đầu học ngay") { dialog, _ ->
          dialog.dismiss()
        }
        .setCancelable(false)
        .show()
    }
  }

  override fun onPause() {
    adManager.pause()
    webView?.onPause()
    webView?.pauseTimers()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    adManager.resume()
    webView?.onResume()
    webView?.resumeTimers()
    
    // Đồng bộ trạng thái khi mở lại ứng dụng
    updateAdMobForVipStatus()
  }

  override fun onDestroy() {
    try {
      billingManager.destroy()
    } catch (_: Exception) {}
    adManager.destroy()
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
  fun getGithubQuestionsJson(grade: Int, round: Int): String {
    return activity.githubRepo.getCachedQuestionsJson(grade, round) ?: ""
  }

  @JavascriptInterface
  fun fetchGithubQuestions(grade: Int, round: Int) {
    activity.githubRepo.fetchAndCacheQuestions(grade, round, onSuccess = {
      activity.runOnUiThread {
        webViewProvider()?.evaluateJavascript(
          "if (window.onGithubQuestionsUpdated) { window.onGithubQuestionsUpdated($grade, $round); }",
          null
        )
      }
    })
  }

  @JavascriptInterface
  fun syncAllGithubQuestions(force: Boolean = true) {
    activity.githubRepo.syncRepoQuestions(forceRefresh = force) { success, message ->
      activity.runOnUiThread {
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
        activity.startActivity(intent)
      } catch (e: Exception) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/sieutrangnhi-tiengviet/trang-ch%E1%BB%A7")))
      }
    }
  }
}
