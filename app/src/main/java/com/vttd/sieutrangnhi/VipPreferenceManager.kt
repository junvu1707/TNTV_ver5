package com.vttd.sieutrangnhi

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

class VipPreferenceManager(context: Context) {
  companion object {
    const val PREF_NAME = "trang_nhi_vip_prefs"
    const val KEY_VIP_LEVEL = "vip_level"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_NAME = "user_name"
    const val KEY_PURCHASE_TOKEN = "purchase_token"
    const val KEY_LAST_ORDER_ID = "last_order_id"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_UNLOCKED_ROUNDS = "unlocked_rounds"
    const val KEY_VIP1_EXPIRY_TIMESTAMP = "vip1_expiry_timestamp"

    const val VIP_LEVEL_0 = 0 // Miễn phí
    const val VIP_LEVEL_1 = 1 // VIP 1 (Thời hạn 1 năm từ khi đăng ký)
    const val VIP_LEVEL_2 = 2 // VIP 2 Trọn đời

    const val ONE_YEAR_IN_MS = 365L * 24L * 60L * 60L * 1000L // 365 ngày (1 năm)
  }

  private val appContext: Context = context.applicationContext
  private val prefs: SharedPreferences =
    appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  // In-memory cache để tối ưu hiệu năng đọc bridge từ JavaScript
  @Volatile private var cachedVipLevel: Int? = null
  @Volatile private var cachedUserEmail: String? = null
  @Volatile private var cachedUserName: String? = null
  @Volatile private var cachedDeviceId: String? = null
  @Volatile private var cachedPurchaseToken: String? = null
  @Volatile private var cachedLastOrderId: String? = null

  fun getVipLevel(): Int {
    val cached = cachedVipLevel
    val level = cached ?: prefs.getInt(KEY_VIP_LEVEL, VIP_LEVEL_0).also { cachedVipLevel = it }
    if (level == VIP_LEVEL_1) {
      val expiryTime = prefs.getLong(KEY_VIP1_EXPIRY_TIMESTAMP, 0L)
      if (expiryTime > 0L && System.currentTimeMillis() > expiryTime) {
        // Đã hết hạn 1 năm kể từ thời điểm đăng ký -> Tự động đưa về tài khoản miễn phí
        setVipLevel(VIP_LEVEL_0)
        return VIP_LEVEL_0
      }
    }
    return level
  }

  fun setVipLevel(level: Int) {
    cachedVipLevel = level
    val editor = prefs.edit().putInt(KEY_VIP_LEVEL, level)
    if (level == VIP_LEVEL_1) {
      val currentExpiry = prefs.getLong(KEY_VIP1_EXPIRY_TIMESTAMP, 0L)
      if (currentExpiry <= 0L || System.currentTimeMillis() > currentExpiry) {
        val newExpiry = System.currentTimeMillis() + ONE_YEAR_IN_MS
        editor.putLong(KEY_VIP1_EXPIRY_TIMESTAMP, newExpiry)
      }
    }
    editor.apply()
  }

  fun getVip1ExpiryTimestamp(): Long {
    return prefs.getLong(KEY_VIP1_EXPIRY_TIMESTAMP, 0L)
  }

  fun isVip(): Boolean = getVipLevel() > VIP_LEVEL_0

  fun isVip2(): Boolean = getVipLevel() >= VIP_LEVEL_2

  fun shouldShowBannerAd(): Boolean {
    return getVipLevel() < VIP_LEVEL_2
  }

  fun shouldShowInterstitialAd(): Boolean {
    val level = getVipLevel()
    if (level >= VIP_LEVEL_2) return false
    if (level == VIP_LEVEL_1) {
      return Math.random() < 0.20
    }
    return true
  }

  fun getDeviceId(): String {
    cachedDeviceId?.let { return it }
    var id = prefs.getString(KEY_DEVICE_ID, null)
    if (id.isNullOrEmpty()) {
      id = try {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
      } catch (_: Exception) {
        null
      }
      if (id.isNullOrEmpty()) {
        id = UUID.randomUUID().toString().replace("-", "").take(16)
      }
      prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    }
    cachedDeviceId = id
    return id
  }

  fun getUserEmail(): String {
    cachedUserEmail?.let { return it }
    val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    cachedUserEmail = email
    return email
  }

  fun setUserEmail(email: String) {
    cachedUserEmail = email
    prefs.edit().putString(KEY_USER_EMAIL, email).apply()
  }

  fun getUserName(): String {
    cachedUserName?.let { return it }
    val name = prefs.getString(KEY_USER_NAME, "") ?: ""
    cachedUserName = name
    return name
  }

  fun setUserName(name: String) {
    cachedUserName = name
    prefs.edit().putString(KEY_USER_NAME, name).apply()
  }

  fun getPurchaseToken(): String {
    cachedPurchaseToken?.let { return it }
    val token = prefs.getString(KEY_PURCHASE_TOKEN, "") ?: ""
    cachedPurchaseToken = token
    return token
  }

  fun setPurchaseToken(token: String) {
    cachedPurchaseToken = token
    prefs.edit().putString(KEY_PURCHASE_TOKEN, token).apply()
  }

  fun getLastOrderId(): String {
    cachedLastOrderId?.let { return it }
    val orderId = prefs.getString(KEY_LAST_ORDER_ID, "") ?: ""
    cachedLastOrderId = orderId
    return orderId
  }

  fun setLastOrderId(orderId: String) {
    cachedLastOrderId = orderId
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
