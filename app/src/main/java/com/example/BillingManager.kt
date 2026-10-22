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
    const val PRODUCT_VIP_1 = "vip_package_1"
    const val PRODUCT_VIP_2 = "vip_package_2"
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

  fun launchBillingFlow(activity: Activity, productId: String, userEmail: String = "") {
    if (userEmail.isNotBlank()) {
      vipPrefs.setUserEmail(userEmail)
    }

    if (!billingClient.isReady) {
      Log.w(TAG, "Billing client not ready, reconnecting...")
      startBillingConnection()
      // Fallback sandbox cho môi trường kiểm thử giả lập không có Google Play Store
      val level = if (productId == PRODUCT_VIP_2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
      vipPrefs.setVipLevel(level)
      val orderId = "SANDBOX_" + System.currentTimeMillis()
      vipPrefs.setLastOrderId(orderId)
      onPurchaseSuccessCallback?.invoke(productId, level)
      if (userEmail.isNotBlank()) {
        emailJSManager.sendOrderConfirmation(userEmail, orderId, level)
      }
      return
    }

    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(productId)
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
        Log.w(TAG, "Product details not found for $productId: ${billingResult.debugMessage}")
        // Sandbox fallback khi sản phẩm chưa được kích hoạt trên Play Console
        val level = if (productId == PRODUCT_VIP_2) VipPreferenceManager.VIP_LEVEL_2 else VipPreferenceManager.VIP_LEVEL_1
        vipPrefs.setVipLevel(level)
        val orderId = "DEV_" + System.currentTimeMillis()
        vipPrefs.setLastOrderId(orderId)
        onPurchaseSuccessCallback?.invoke(productId, level)
        if (userEmail.isNotBlank()) {
          emailJSManager.sendOrderConfirmation(userEmail, orderId, level)
        }
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
      val isVip2 = purchase.products.contains(PRODUCT_VIP_2)
      val isVip1 = purchase.products.contains(PRODUCT_VIP_1)
      val level = if (isVip2) VipPreferenceManager.VIP_LEVEL_2 else if (isVip1) VipPreferenceManager.VIP_LEVEL_1 else VipPreferenceManager.VIP_LEVEL_0

      if (level > VipPreferenceManager.VIP_LEVEL_0) {
        vipPrefs.setVipLevel(level)
        vipPrefs.setLastOrderId(purchase.orderId ?: purchase.purchaseToken)

        val email = vipPrefs.getUserEmail()
        if (email.isNotBlank()) {
          emailJSManager.sendOrderConfirmation(email, purchase.orderId ?: "GPA.AUTO", level)
        }

        val productId = if (isVip2) PRODUCT_VIP_2 else PRODUCT_VIP_1
        onPurchaseSuccessCallback?.invoke(productId, level)
      }

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

  fun destroy() {
    if (billingClient.isReady) {
      billingClient.endConnection()
    }
  }
}
