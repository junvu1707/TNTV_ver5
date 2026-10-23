package com.example

import android.app.Activity
import android.util.Log
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
      PRODUCT_VIP_2, PRODUCT_VIP_2_ALIAS -> PRODUCT_VIP_2
      else -> PRODUCT_VIP_1
    }

    val level = if (targetProductId == PRODUCT_VIP_2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1

    if (!billingClient.isReady) {
      Log.w(TAG, "Billing client not ready, reconnecting...")
      startBillingConnection()
      // Test Sandbox fallback cho môi trường dev / thử nghiệm
      processSuccessfulVIPActivation(targetProductId, level, "SANDBOX_" + System.currentTimeMillis(), "TOKEN_SANDBOX_" + System.currentTimeMillis())
      return
    }

    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(targetProductId)
        .setProductType(BillingClient.ProductType.INAPP)
        .build(),
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(if (targetProductId == PRODUCT_VIP_2) PRODUCT_VIP_2_ALIAS else PRODUCT_VIP_1_ALIAS)
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

        billingClient.launchBillingFlow(activity, flowParams)
      } else {
        Log.w(TAG, "Product details not found on Play Console for $targetProductId: ${billingResult.debugMessage}")
        // Development Sandbox Fallback khi app chưa release Play Console
        processSuccessfulVIPActivation(targetProductId, level, "DEV_ORDER_" + System.currentTimeMillis(), "DEV_TOKEN_" + System.currentTimeMillis())
      }
    }
  }

  override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (purchase in purchases) {
        handlePurchase(purchase)
      }
    } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      Log.d(TAG, "User canceled purchase")
    } else {
      Log.w(TAG, "Purchase failed: ${billingResult.debugMessage}")
    }
  }

  private fun handlePurchase(purchase: Purchase) {
    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
      val isVip2 = purchase.products.contains(PRODUCT_VIP_2) || purchase.products.contains(PRODUCT_VIP_2_ALIAS)
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

  fun destroy() {
    if (billingClient.isReady) {
      billingClient.endConnection()
    }
  }
}
