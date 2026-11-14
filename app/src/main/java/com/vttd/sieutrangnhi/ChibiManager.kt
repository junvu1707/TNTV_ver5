package com.vttd.sieutrangnhi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ChibiManager(private val context: Context) {

    companion object {
        private const val TAG = "ChibiManager"
        const val GITHUB_CHIBI_BASE_URL = "https://raw.githubusercontent.com/junvu1707/app_chibi/main/"
        const val CHIBIS_JSON_URL = "https://raw.githubusercontent.com/junvu1707/app_chibi/main/chibis.json"

        private val ASSET_MAP = mapOf(
            "chú bé dễ thương.png" to "chu_be_de_thuong.png",
            "cô bé nơ đỏ.png" to "co_be_no_do.png",
            "bé cute.png" to "be_cute.png",
            "bé thỏ.png" to "be_tho.png",
            "cô bé kem.png" to "co_be_kem.png",
            "cậu bé vui vẻ.png" to "cau_be_vui_ve.png",
            "cậu bé ấm áp.png" to "cau_be_am_ap.png",
            "cô bé xinh xắn.png" to "co_be_xinh_xan.png",
            "cô bé tóc dài.png" to "co_be_toc_dai.png",
            "cô bé áo dài.png" to "co_be_ao_dai.png",
            "cậu bé đáng yêu.png" to "cau_be_dang_yeu.png",
            "công chúa váy xanh.png" to "cong_chua_vay_xanh.png",
            "cô bé nhút nhát.png" to "co_be_nhut_nhat.png"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val cacheFile: File by lazy {
        File(context.filesDir, "chibis_cache.json")
    }

    /**
     * Lấy chuỗi JSON danh sách Chibi đã lưu trữ trong cache hoặc đọc từ asset fallback
     */
    fun getCachedChibisJson(): String {
        try {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return cacheFile.readText(StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc cache chibis: ${e.message}")
        }
        return getLocalAssetChibisJson()
    }

    fun getLocalAssetChibisJson(): String {
        return try {
            context.assets.open("chibis.json").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc chibis.json từ assets: ${e.message}")
            ""
        }
    }

    /**
     * Tự động tải danh sách chibis.json mới nhất từ GitHub repository và cập nhật cache
     */
    fun fetchAndCacheChibis(onSuccess: ((String) -> Unit)? = null) {
        scope.launch {
            try {
                val request = Request.Builder()
                    .url(CHIBIS_JSON_URL)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank() && bodyStr.trim().startsWith("[")) {
                        cacheFile.writeText(bodyStr, StandardCharsets.UTF_8)
                        Log.d(TAG, "Đã cập nhật chibis.json thành công từ GitHub")
                        onSuccess?.invoke(bodyStr)
                    }
                } else {
                    Log.w(TAG, "Tải chibis.json từ GitHub thất bại: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi kết nối tải chibis.json: ${e.message}")
            }
        }
    }

    /**
     * Ưu tiên lấy URL từ repository GitHub app_chibi
     */
    fun getChibiGithubUrl(fileName: String): String {
        if (fileName.isBlank()) return "app_logo.png"
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            return fileName
        }
        val trimmed = fileName.trim()
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "$GITHUB_CHIBI_BASE_URL$encoded"
    }

    /**
     * Lấy đường dẫn file dự phòng trong assets nếu không tải được từ GitHub
     */
    fun getChibiFallbackAsset(fileName: String): String {
        val trimmed = fileName.trim()
        return ASSET_MAP[trimmed] ?: trimmed
    }

    fun getChibiUriForWebView(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) {
            return "file://${file.absolutePath}"
        }
        val assetName = getChibiFallbackAsset(fileName)
        return "file:///android_asset/$assetName"
    }
}
