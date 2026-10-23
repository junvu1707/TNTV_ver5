package com.example

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

class VipPreferenceManager(private val context: Context) {
  companion object {
    const val PREF_NAME = "trang_nhi_vip_prefs"
    const val KEY_VIP_LEVEL = "vip_level"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_NAME = "user_name"
    const val KEY_PURCHASE_TOKEN = "purchase_token"
    const val KEY_LAST_ORDER_ID = "last_order_id"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_UNLOCKED_ROUNDS = "unlocked_rounds"

    const val VIP_LEVEL_0 = 0 // Miễn phí
    const val VIP_LEVEL_1 = 1 // VIP 1
    const val VIP_LEVEL_2 = 2 // VIP 2 Trọn đời
  }

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  fun getVipLevel(): Int {
    return prefs.getInt(KEY_VIP_LEVEL, VIP_LEVEL_0)
  }

  fun setVipLevel(level: Int) {
    prefs.edit().putInt(KEY_VIP_LEVEL, level).apply()
  }

  fun isVip(): Boolean = getVipLevel() > VIP_LEVEL_0

  fun isVip2(): Boolean = getVipLevel() >= VIP_LEVEL_2

  fun shouldShowBannerAd(): Boolean {
    // VIP 2 miễn phí quảng cáo banner 100%
    return getVipLevel() < VIP_LEVEL_2
  }

  fun shouldShowInterstitialAd(): Boolean {
    val level = getVipLevel()
    if (level >= VIP_LEVEL_2) return false
    if (level == VIP_LEVEL_1) {
      // VIP 1 chỉ hiện 20% tần suất
      return Math.random() < 0.20
    }
    return true
  }

  fun getDeviceId(): String {
    var id = prefs.getString(KEY_DEVICE_ID, null)
    if (id.isNullOrEmpty()) {
      id = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
      } catch (e: Exception) {
        null
      }
      if (id.isNullOrEmpty()) {
        id = UUID.randomUUID().toString().replace("-", "").take(16)
      }
      prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    }
    return id
  }

  fun getUserEmail(): String {
    return prefs.getString(KEY_USER_EMAIL, "") ?: ""
  }

  fun setUserEmail(email: String) {
    prefs.edit().putString(KEY_USER_EMAIL, email).apply()
  }

  fun getUserName(): String {
    return prefs.getString(KEY_USER_NAME, "") ?: ""
  }

  fun setUserName(name: String) {
    prefs.edit().putString(KEY_USER_NAME, name).apply()
  }

  fun getPurchaseToken(): String {
    return prefs.getString(KEY_PURCHASE_TOKEN, "") ?: ""
  }

  fun setPurchaseToken(token: String) {
    prefs.edit().putString(KEY_PURCHASE_TOKEN, token).apply()
  }

  fun getLastOrderId(): String {
    return prefs.getString(KEY_LAST_ORDER_ID, "") ?: ""
  }

  fun setLastOrderId(orderId: String) {
    prefs.edit().putString(KEY_LAST_ORDER_ID, orderId).apply()
  }

  fun isRoundUnlocked(roundIndex: Int): Boolean {
    if (roundIndex <= 6) return true // Vòng 1-6 luôn miễn phí
    if (isVip()) return true // Thành viên VIP mở tất cả vòng
    val set = prefs.getStringSet(KEY_UNLOCKED_ROUNDS, emptySet()) ?: emptySet()
    return set.contains(roundIndex.toString())
  }

  fun unlockRound(roundIndex: Int) {
    val set = (prefs.getStringSet(KEY_UNLOCKED_ROUNDS, emptySet()) ?: emptySet()).toMutableSet()
    set.add(roundIndex.toString())
    prefs.edit().putStringSet(KEY_UNLOCKED_ROUNDS, set).apply()
  }
}
