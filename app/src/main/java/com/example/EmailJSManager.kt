package com.example

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

class EmailJSManager {
  companion object {
    private const val TAG = "EmailJSManager"
    private const val SERVICE_ID = "service_trang_nhi"
    private const val TEMPLATE_ID = "template_vip_confirm"
    private const val PUBLIC_KEY = "user_trangnhi_placeholder"
    private const val ENDPOINT = "https://api.emailjs.com/api/v1.0/email/send"
  }

  private val client = OkHttpClient()
  private val mainHandler = Handler(Looper.getMainLooper())

  fun sendOrderConfirmation(
    email: String,
    orderId: String,
    vipLevel: Int,
    callback: ((Boolean) -> Unit)? = null
  ) {
    if (email.isBlank() || !email.contains("@")) {
      callback?.invoke(false)
      return
    }

    val jsonBody = JSONObject().apply {
      put("service_id", SERVICE_ID)
      put("template_id", TEMPLATE_ID)
      put("user_id", PUBLIC_KEY)
      put("template_params", JSONObject().apply {
        put("to_email", email)
        put("order_id", orderId)
        put("vip_package", if (vipLevel >= 2) "VIP 2 (Trọn đời)" else "VIP 1 (Mùa thi)")
      })
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
}
