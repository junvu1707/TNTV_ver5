package com.sieutrangnhi

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
    val inputEmail = userEmail.trim()
    val inputName = userName.trim()

    if (inputEmail.isBlank()) {
      activity.runOnUiThread {
        callback(
          false,
          VipPreferenceManager.VIP_LEVEL_0,
          "Vui lòng nhập Email đã dùng khi đăng ký mua VIP để khôi phục!"
        )
      }
      return
    }

    if (!billingClient.isReady) {
      billingClient.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
          if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            isServiceConnected = true
            executeScanPurchases(inputName, inputEmail, callback)
          } else {
            executeLocalFallbackScan(inputName, inputEmail, callback)
          }
        }

        override fun onBillingServiceDisconnected() {
          executeLocalFallbackScan(inputName, inputEmail, callback)
        }
      })
      return
    }

    executeScanPurchases(inputName, inputEmail, callback)
  }

  private fun executeScanPurchases(
    inputName: String,
    inputEmail: String,
    callback: (Boolean, Int, String) -> Unit
  ) {
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
          val savedEmail = vipPrefs.getUserEmail().trim()
          val deviceId = vipPrefs.getDeviceId()

          // Kiểm tra xem Email nhập vào có khớp với Email đã đăng ký VIP cho thiết bị này không
          if (savedEmail.isNotEmpty() && !savedEmail.equals(inputEmail, ignoreCase = true)) {
            activity.runOnUiThread {
              callback(
                false,
                VipPreferenceManager.VIP_LEVEL_0,
                "Khôi phục thất bại! Email '$inputEmail' không khớp với Email đã đăng ký VIP ($savedEmail) của thiết bị này (ID: $deviceId)!"
              )
            }
            return@queryPurchasesAsync
          }

          vipPrefs.setUserEmail(inputEmail)
          if (inputName.isNotEmpty()) vipPrefs.setUserName(inputName)
          vipPrefs.setVipLevel(highestLevel)
          if (activeToken.isNotBlank()) vipPrefs.setPurchaseToken(activeToken)

          activity.runOnUiThread {
            callback(true, highestLevel, "Đã khôi phục thành công gói VIP cho Email '$inputEmail' và ID thiết bị $deviceId!")
          }
        } else {
          executeLocalFallbackScan(inputName, inputEmail, callback)
        }
      } else {
        executeLocalFallbackScan(inputName, inputEmail, callback)
      }
    }
  }

  private fun executeLocalFallbackScan(
    inputName: String,
    inputEmail: String,
    callback: (Boolean, Int, String) -> Unit
  ) {
    val savedLevel = vipPrefs.getVipLevel()
    val savedEmail = vipPrefs.getUserEmail().trim()
    val deviceId = vipPrefs.getDeviceId()

    // Yêu cầu bắt buộc: Đã có VIP (>0), đã có Email đăng ký (>0) VÀ Email nhập vào phải KHỚP 100% với Email đã đăng ký + ID thiết bị
    if (savedLevel > VipPreferenceManager.VIP_LEVEL_0 &&
        savedEmail.isNotEmpty() &&
        savedEmail.equals(inputEmail, ignoreCase = true)
    ) {
      if (inputName.isNotEmpty()) vipPrefs.setUserName(inputName)
      activity.runOnUiThread {
        callback(
          true,
          savedLevel,
          "Xác thực thành công tài khoản VIP cho Email '$inputEmail' và ID thiết bị $deviceId!"
        )
      }
    } else {
      activity.runOnUiThread {
        callback(
          false,
          VipPreferenceManager.VIP_LEVEL_0,
          "Khôi phục thất bại! Email '$inputEmail' không khớp với thông tin đã đăng ký VIP cho ID thiết bị $deviceId."
        )
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
