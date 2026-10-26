package com.vttd.sieutrangnhi

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmailJSManager {
  companion object {
    private const val TAG = "EmailJSManager"
    const val ADMIN_EMAIL = "junvu1707@gmail.com"
    private const val SERVICE_ID = "service_trang_nhi"
    private const val TEMPLATE_ID = "template_vip_confirm"
    private const val PUBLIC_KEY = "user_trangnhi_placeholder"
    private const val ENDPOINT = "https://api.emailjs.com/api/v1.0/email/send"
  }

  private val client = OkHttpClient()
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * Tự động gửi thông tin kích hoạt VIP đến Quản trị viên (junvu1707@gmail.com) và người dùng
   */
  fun sendVipActivationNotification(
    userEmail: String,
    userName: String,
    vipType: String,
    deviceId: String,
    purchaseToken: String,
    orderId: String,
    callback: ((Boolean) -> Unit)? = null
  ) {
    val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

    val templateParams = JSONObject().apply {
      put("admin_email", ADMIN_EMAIL)
      put("to_email", if (userEmail.isNotBlank()) userEmail else ADMIN_EMAIL)
      put("user_email", userEmail)
      put("user_name", if (userName.isNotBlank()) userName else "Thí sinh nhí")
      put("vip_type", vipType)
      put("device_id", deviceId)
      put("purchase_token", purchaseToken)
      put("order_id", orderId)
      put("purchase_date", currentTime)
      put("app_name", "Siêu Trạng Nhí Tiếng Việt")
      put("message", "Thanh toán VIP thành công cho $userName ($userEmail) trên thiết bị $deviceId vào lúc $currentTime.")
    }

    val jsonBody = JSONObject().apply {
      put("service_id", SERVICE_ID)
      put("template_id", TEMPLATE_ID)
      put("user_id", PUBLIC_KEY)
      put("template_params", templateParams)
    }

    val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
      .url(ENDPOINT)
      .post(requestBody)
      .build()

    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        Log.w(TAG, "EmailJS send failed: ${e.message}")
        mainHandler.post { callback?.invoke(false) }
      }

      override fun onResponse(call: Call, response: Response) {
        val success = response.isSuccessful
        response.close()
        Log.d(TAG, "EmailJS response success: $success")
        mainHandler.post { callback?.invoke(success) }
      }
    })
  }

  fun sendOrderConfirmation(
    email: String,
    orderId: String,
    vipLevel: Int,
    callback: ((Boolean) -> Unit)? = null
  ) {
    val vipType = if (vipLevel >= 2) "VIP 2 (Trọn đời)" else "VIP 1 (Mùa thi)"
    sendVipActivationNotification(
      userEmail = email,
      userName = "",
      vipType = vipType,
      deviceId = "",
      purchaseToken = "",
      orderId = orderId,
      callback = callback
    )
  }
}
