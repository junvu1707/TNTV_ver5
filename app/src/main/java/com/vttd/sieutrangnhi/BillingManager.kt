package com.vttd.sieutrangnhi

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*

class BillingManager(
  private val activity: Activity,
  private val vipPrefs: VipPreferenceManager,
  private val emailJSManager: EmailJSManager
) : PurchasesUpdatedListener {

  companion object {
    private const val TAG = "BillingManager"
    // ID Sản phẩm chính xác trên Google Play Console
    const val PRODUCT_VIP_1 = "vip1_yearly"   // Gói VIP 1 (49.000đ / 1 năm) - Loại SUBS
    const val PRODUCT_VIP_2 = "vip2_trondoi"  // Gói VIP 2 (99.000đ / trọn đời) - Loại INAPP

    /**
     * Ánh xạ mã sản phẩm từ giao diện/JS thành ID chính xác trên Google Play Console
     */
    fun getPlayConsoleProductId(inputCode: String): String {
      val cleaned = inputCode.lowercase().trim()
      return if (cleaned == "vip2" || cleaned == "vip2_trondoi" || cleaned == "vip_package_2" || cleaned == "vip_2_trondoi") {
        PRODUCT_VIP_2
      } else {
        PRODUCT_VIP_1
      }
    }

    /**
     * Xác định Loại sản phẩm: SUBS (Thuê bao) hay INAPP (Một lần)
     */
    fun getProductType(productId: String): String {
      val consoleId = getPlayConsoleProductId(productId)
      return if (consoleId == PRODUCT_VIP_2) {
        BillingClient.ProductType.INAPP
      } else {
        BillingClient.ProductType.SUBS
      }
    }

    fun isVip2Product(productId: String): Boolean {
      val cleaned = productId.lowercase().trim()
      return cleaned == PRODUCT_VIP_2 || cleaned == "vip2" || cleaned == "vip_package_2" || cleaned == "vip_2_trondoi"
    }
    private const val MAX_RECONNECT_ATTEMPTS = 3
  }

  var onPurchaseSuccessCallback: ((productId: String, vipLevel: Int) -> Unit)? = null

  private val mainHandler = Handler(Looper.getMainLooper())
  private var reconnectAttempts = 0

  private var billingClient: BillingClient = BillingClient.newBuilder(activity)
    .setListener(this)
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()
    )
    .build()

  @Volatile
  private var isServiceConnected = false

  init {
    startBillingConnection()
  }

  private fun startBillingConnection(onConnected: (() -> Unit)? = null) {
    if (billingClient.isReady) {
      isServiceConnected = true
      onConnected?.invoke()
      return
    }

    billingClient.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
          Log.d(TAG, "Billing Service Connected successfully")
          isServiceConnected = true
          reconnectAttempts = 0
          queryPurchases()
          onConnected?.invoke()
        } else {
          Log.w(TAG, "Billing Setup failed: ${billingResult.debugMessage}")
          scheduleReconnect()
        }
      }

      override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing Service Disconnected")
        isServiceConnected = false
        scheduleReconnect()
      }
    })
  }

  private fun scheduleReconnect() {
    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
      reconnectAttempts++
      val delay = (reconnectAttempts * 3000L) // 3s, 6s, 9s
      Log.d(TAG, "Scheduling Billing reconnect attempt $reconnectAttempts in ${delay}ms")
      mainHandler.postDelayed({
        if (!billingClient.isReady) {
          startBillingConnection()
        }
      }, delay)
    }
  }

  fun queryPurchases() {
    if (!billingClient.isReady) {
      startBillingConnection { queryPurchases() }
      return
    }

    // 1. Truy vấn sản phẩm mua 1 lần (INAPP)
    val inAppParams = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build()

    billingClient.queryPurchasesAsync(inAppParams) { billingResult, purchasesList ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        for (purchase in purchasesList) {
          handlePurchase(purchase)
        }
      }
    }

    // 2. Truy vấn sản phẩm gói thuê bao (SUBS)
    val subsParams = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.SUBS)
      .build()

    billingClient.queryPurchasesAsync(subsParams) { billingResult, purchasesList ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        for (purchase in purchasesList) {
          handlePurchase(purchase)
        }
      }
    }
  }

  fun launchBillingFlow(
    activity: Activity,
    productId: String,
    userEmail: String = "",
    userName: String = ""
  ) {
    if (userEmail.isNotBlank()) {
      vipPrefs.setUserEmail(userEmail)
    }
    if (userName.isNotBlank()) {
      vipPrefs.setUserName(userName)
    }

    val targetProductId = getPlayConsoleProductId(productId)
    val targetProductType = getProductType(targetProductId)

    if (!billingClient.isReady) {
      Log.w(TAG, "Billing client not ready, reconnecting...")
      startBillingConnection {
        launchBillingFlow(activity, productId, userEmail, userName)
      }
      showToastOnUi("Đang kết nối dịch vụ Google Play, vui lòng thử lại sau giây lát...")
      return
    }

    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(targetProductId)
        .setProductType(targetProductType)
        .build()
    )

    val queryParams = QueryProductDetailsParams.newBuilder()
      .setProductList(productList)
      .build()

    billingClient.queryProductDetailsAsync(queryParams) { billingResult, queryProductDetailsResult ->
      val productDetailsList = queryProductDetailsResult.productDetailsList
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
        val productDetails = productDetailsList[0]

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
          .setProductDetails(productDetails)

        // Đối với gói Thuê bao (SUBS), Google Play v5+ bắt buộc phải cung cấp offerToken
        if (targetProductType == BillingClient.ProductType.SUBS) {
          val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
          if (!offerToken.isNullOrBlank()) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
          } else {
            Log.w(TAG, "No subscription offer token found for $targetProductId")
          }
        }

        val flowParams = BillingFlowParams.newBuilder()
          .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
          .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "launchBillingFlow response code: ${result.responseCode}")
      } else {
        Log.w(TAG, "Product details not found on Play Console for $targetProductId ($targetProductType): ${billingResult.debugMessage}")
        showToastOnUi("Không thể lấy thông tin giá từ Google Play (${billingResult.debugMessage}). Vui lòng kiểm tra tài khoản trên Google Play Console.")
      }
    }
  }

  override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    when (billingResult.responseCode) {
      BillingClient.BillingResponseCode.OK -> {
        if (!purchases.isNullOrEmpty()) {
          for (purchase in purchases) {
            handlePurchase(purchase)
          }
        }
      }
      BillingClient.BillingResponseCode.USER_CANCELED -> {
        Log.d(TAG, "User canceled purchase")
        showToastOnUi("Bạn đã hủy giao dịch thanh toán Google Play.")
      }
      BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
        Log.d(TAG, "Item already owned")
        showToastOnUi("Tài khoản Google của bạn đã sở hữu gói VIP này. Đang khôi phục quyền lợi...")
        queryPurchases()
      }
      else -> {
        Log.w(TAG, "Purchase failed: ${billingResult.debugMessage}")
        showToastOnUi("Thanh toán Google Play không thành công: ${billingResult.debugMessage}")
      }
    }
  }

  private fun handlePurchase(purchase: Purchase) {
    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
      val isVip2 = purchase.products.any { isVip2Product(it) }
      val level = if (isVip2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
      val productId = if (isVip2) PRODUCT_VIP_2 else PRODUCT_VIP_1

      processSuccessfulVIPActivation(productId, level, purchase.orderId ?: purchase.purchaseToken, purchase.purchaseToken)

      if (!purchase.isAcknowledged) {
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
          .setPurchaseToken(purchase.purchaseToken)
          .build()
        billingClient.acknowledgePurchase(acknowledgeParams) { result ->
          Log.d(TAG, "Purchase acknowledged: ${result.responseCode}")
        }
      }
    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
      showToastOnUi("Giao dịch đang được xử lý bởi Google Play. Vui lòng chờ xác nhận...")
    }
  }

  private fun processSuccessfulVIPActivation(
    productId: String,
    vipLevel: Int,
    orderId: String,
    purchaseToken: String
  ) {
    val userEmail = vipPrefs.getUserEmail()
    val userName = vipPrefs.getUserName()
    val deviceId = vipPrefs.getDeviceId()

    vipPrefs.saveVipPurchaseRecord(userEmail, deviceId, vipLevel)
    vipPrefs.setLastOrderId(orderId)
    vipPrefs.setPurchaseToken(purchaseToken)

    val vipTypeName = if (vipLevel >= 2) "Gói VIP 2 (Trọn Đời)" else "Gói VIP 1 (1 Năm)"

    // Gửi email tự động qua EmailJS
    emailJSManager.sendVipActivationNotification(
      userEmail = userEmail,
      userName = userName,
      vipType = vipTypeName,
      deviceId = deviceId,
      purchaseToken = purchaseToken,
      orderId = orderId
    ) { success ->
      Log.d(TAG, "Automated EmailJS activation sent: $success")
    }

    activity.runOnUiThread {
      onPurchaseSuccessCallback?.invoke(productId, vipLevel)
    }
  }

  fun scanAndRestoreVip(
    userName: String = "",
    userEmail: String = "",
    callback: (success: Boolean, vipLevel: Int, message: String) -> Unit
  ) {
    val inputEmail = userEmail.trim()
    val inputName = userName.trim()

    if (!billingClient.isReady) {
      startBillingConnection {
        executeScanPurchases(inputName, inputEmail, callback)
      }
      return
    }

    executeScanPurchases(inputName, inputEmail, callback)
  }

  private fun executeScanPurchases(
    inputName: String,
    inputEmail: String,
    callback: (Boolean, Int, String) -> Unit
  ) {
    // 1. Quét sản phẩm mua một lần (INAPP - VIP 2) từ tài khoản Google Play trên thiết bị
    val inAppParams = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build()

    billingClient.queryPurchasesAsync(inAppParams) { billingResultInApp, purchasesInApp ->
      var highestLevel = VipPreferenceManager.VIP_LEVEL_0
      var activeToken = ""
      var activePurchase: Purchase? = null

      if (billingResultInApp.responseCode == BillingClient.BillingResponseCode.OK && !purchasesInApp.isNullOrEmpty()) {
        for (purchase in purchasesInApp) {
          if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val isVip2 = purchase.products.any { isVip2Product(it) }
            val level = if (isVip2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
            if (level > highestLevel) {
              highestLevel = level
              activeToken = purchase.purchaseToken
              activePurchase = purchase
            }
          }
        }
      }

      // 2. Quét sản phẩm gói thuê bao (SUBS - VIP 1) từ tài khoản Google Play trên thiết bị
      val subsParams = QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.SUBS)
        .build()

      billingClient.queryPurchasesAsync(subsParams) { billingResultSubs, purchasesSubs ->
        if (billingResultSubs.responseCode == BillingClient.BillingResponseCode.OK && !purchasesSubs.isNullOrEmpty()) {
          for (purchase in purchasesSubs) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
              val isVip2 = purchase.products.any { isVip2Product(it) }
              val level = if (isVip2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
              if (level > highestLevel) {
                highestLevel = level
                activeToken = purchase.purchaseToken
                activePurchase = purchase
              }
            }
          }
        }

        if (highestLevel > VipPreferenceManager.VIP_LEVEL_0) {
          val currentDeviceId = vipPrefs.getDeviceId()
          val saveEmail = inputEmail.ifBlank { vipPrefs.getUserEmail() }
          vipPrefs.saveVipPurchaseRecord(saveEmail, currentDeviceId, highestLevel)
          if (inputName.isNotEmpty()) vipPrefs.setUserName(inputName)
          if (activeToken.isNotBlank()) vipPrefs.setPurchaseToken(activeToken)

          // Xác nhận mua hàng nếu chưa được acknowledge
          activePurchase?.let { purchase ->
            if (!purchase.isAcknowledged) {
              val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
              billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                Log.d(TAG, "Restored purchase acknowledged: ${result.responseCode}")
              }
            }
          }

          val vipName = if (highestLevel >= 2) "VIP 2 (Trọn Đời)" else "VIP 1 (1 Năm)"
          activity.runOnUiThread {
            callback(
              true,
              highestLevel,
              "Đã khôi phục thành công gói $vipName từ tài khoản Google Play!"
            )
          }
        } else {
          activity.runOnUiThread {
            callback(
              false,
              VipPreferenceManager.VIP_LEVEL_0,
              "Không tìm thấy thông tin mua VIP trên tài khoản Google Play này. Vui lòng kiểm tra lại tài khoản Google đăng nhập trên thiết bị."
            )
          }
        }
      }
    }
  }

  private fun showToastOnUi(message: String) {
    activity.runOnUiThread {
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
  }

  fun destroy() {
    mainHandler.removeCallbacksAndMessages(null)
    if (billingClient.isReady) {
      billingClient.endConnection()
    }
  }
}
