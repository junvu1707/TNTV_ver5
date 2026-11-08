package com.vttd.sieutrangnhi

import android.content.Context
import java.io.File

class ChibiManager(private val context: Context) {
    suspend fun getChibiUriForWebView(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) {
            return "file://${file.absolutePath}"
        }
        return "file:///android_asset/$fileName"
    }
}
