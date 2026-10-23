package com.example

import android.app.Activity
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
    const val PRODUCT_VIP_1 = "vip1"
    const val PRODUCT_VIP_2 = "vip2"
    const val PRODUCT_VIP_1_ALIAS = "vip_package_1"
    const val PRODUCT_VIP_2_ALIAS = "vip_package_2"
    const val PRODUCT_VIP_2_TRONDOI = "vip_2_trondoi"
  }

  var onPurchaseSuccessCallback: ((productId: String, vipLevel: Int) -> Unit)? = null

  private var billingClient: BillingClient = BillingClient.newBuilder(activity)
    .setListener(this)
    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
    .build()

  private var isServiceConnected = false

  init {
    startBillingConnection()
  }

  private fun startBillingConnection() {
    billingClient.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
          Log.d(TAG, "Billing Service Connected")
          isServiceConnected = true
          queryPurchases()
        } else {
          Log.w(TAG, "Billing Setup failed: ${billingResult.debugMessage}")
        }
      }

      override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing Service Disconnected")
        isServiceConnected = false
      }
    })
  }

  fun queryPurchases() {
    if (!billingClient.isReady) {
      startBillingConnection()
      return
    }

    val params = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build()

    billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
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

    val targetProductId = when (productId) {
      PRODUCT_VIP_2, PRODUCT_VIP_2_ALIAS, PRODUCT_VIP_2_TRONDOI -> PRODUCT_VIP_2
      else -> PRODUCT_VIP_1
    }

    val aliasProductId = if (targetProductId == PRODUCT_VIP_2) PRODUCT_VIP_2_ALIAS else PRODUCT_VIP_1_ALIAS

    if (!billingClient.isReady) {
      Log.w(TAG, "Billing client not ready, reconnecting...")
      startBillingConnection()
      showToastOnUi("Đang kết nối dịch vụ Google Play, vui lòng bấm Thanh toán lại sau giây lát...")
      return
    }

    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(targetProductId)
        .setProductType(BillingClient.ProductType.INAPP)
        .build(),
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(aliasProductId)
        .setProductType(BillingClient.ProductType.INAPP)
        .build(),
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(PRODUCT_VIP_2_TRONDOI)
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    )

    val queryParams = QueryProductDetailsParams.newBuilder()
      .setProductList(productList)
      .build()

    billingClient.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
        val productDetails = productDetailsList[0]
        val flowParams = BillingFlowParams.newBuilder()
          .setProductDetailsParamsList(
            listOf(
              BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
            )
          )
          .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "launchBillingFlow response code: ${result.responseCode}")
      } else {
        Log.w(TAG, "Product details not found on Play Console for $targetProductId: ${billingResult.debugMessage}")
        showToastOnUi("Không thể lấy thông tin giá từ Google Play (${billingResult.debugMessage}). Vui lòng kiểm tra ứng dụng trên CH Play.")
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
        showToastOnUi("Tài khoản của bạn đã sở hữu gói VIP này. Đang khôi phục quyền lợi...")
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
      val isVip2 = purchase.products.contains(PRODUCT_VIP_2) ||
          purchase.products.contains(PRODUCT_VIP_2_ALIAS) ||
          purchase.products.contains(PRODUCT_VIP_2_TRONDOI)

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
    vipPrefs.setVipLevel(vipLevel)
    vipPrefs.setLastOrderId(orderId)
    vipPrefs.setPurchaseToken(purchaseToken)

    val userEmail = vipPrefs.getUserEmail()
    val userName = vipPrefs.getUserName()
    val deviceId = vipPrefs.getDeviceId()
    val vipTypeName = if (vipLevel >= 2) "Gói VIP 2 (Trọn Đời)" else "Gói VIP 1"

    // Gửi email tự động qua EmailJS về junvu1707@gmail.com
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
    userName: String,
    userEmail: String,
    callback: (success: Boolean, vipLevel: Int, message: String) -> Unit
  ) {
    if (userName.isNotBlank()) vipPrefs.setUserName(userName)
    if (userEmail.isNotBlank()) vipPrefs.setUserEmail(userEmail)

    if (!billingClient.isReady) {
      billingClient.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
          if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            isServiceConnected = true
            executeScanPurchases(callback)
          } else {
            executeLocalFallbackScan(callback)
          }
        }

        override fun onBillingServiceDisconnected() {
          executeLocalFallbackScan(callback)
        }
      })
      return
    }

    executeScanPurchases(callback)
  }

  private fun executeScanPurchases(callback: (Boolean, Int, String) -> Unit) {
    val params = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build()

    billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchasesList.isNotEmpty()) {
        var highestLevel = VipPreferenceManager.VIP_LEVEL_0
        var activeToken = ""

        for (purchase in purchasesList) {
          if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val isVip2 = purchase.products.contains(PRODUCT_VIP_2) ||
                purchase.products.contains(PRODUCT_VIP_2_ALIAS) ||
                purchase.products.contains(PRODUCT_VIP_2_TRONDOI)

            val level = if (isVip2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
            if (level > highestLevel) {
              highestLevel = level
              activeToken = purchase.purchaseToken
            }
          }
        }

        if (highestLevel > VipPreferenceManager.VIP_LEVEL_0) {
          vipPrefs.setVipLevel(highestLevel)
          if (activeToken.isNotBlank()) vipPrefs.setPurchaseToken(activeToken)
          activity.runOnUiThread {
            callback(true, highestLevel, "Đã tìm thấy giao dịch VIP hợp lệ trên Google Play!")
          }
        } else {
          executeLocalFallbackScan(callback)
        }
      } else {
        executeLocalFallbackScan(callback)
      }
    }
  }

  private fun executeLocalFallbackScan(callback: (Boolean, Int, String) -> Unit) {
    val savedLevel = vipPrefs.getVipLevel()
    val savedToken = vipPrefs.getPurchaseToken()
    if (savedLevel > VipPreferenceManager.VIP_LEVEL_0 || savedToken.isNotBlank()) {
      val level = if (savedLevel > 0) savedLevel else VipPreferenceManager.VIP_LEVEL_1
      vipPrefs.setVipLevel(level)
      activity.runOnUiThread {
        callback(true, level, "Đã khôi phục thành công trạng thái VIP từ dữ liệu đã xác thực!")
      }
    } else {
      activity.runOnUiThread {
        callback(false, VipPreferenceManager.VIP_LEVEL_0, "Chưa tìm thấy gói VIP đã đăng ký cho thông tin tài khoản này.")
      }
    }
  }

  private fun showToastOnUi(message: String) {
    activity.runOnUiThread {
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
  }

  fun destroy() {
    if (billingClient.isReady) {
      billingClient.endConnection()
    }
  }
}
