package com.vttd.sieutrangnhi

import android.content.Context
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ChibiManager(private val context: Context) {

    companion object {
        const val GITHUB_CHIBI_BASE_URL = "https://raw.githubusercontent.com/junvu1707/app_chibi/main/"

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
