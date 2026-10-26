package com.vttd.sieutrangnhi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Quản lý tự động tải, đồng bộ & cache file JSON chứa câu hỏi từ GitHub Repository.
 * Thiết kế theo mô hình Offline-First:
 * - Đọc tức thì từ cache cục bộ (Storage/File) nếu thiết bị không có internet.
 * - Tự động tải/cập nhật bất đồng bộ từ GitHub khi có kết nối mạng.
 */
class GitHubQuestionRepository(private val context: Context) {

  companion object {
    private const val TAG = "GitHubQuestionRepo"
    private const val BASE_URL = "https://raw.githubusercontent.com/junvu1707/Trangsinhi_de/main/"

    /**
     * Xác định tên file JSON trên GitHub theo quy tắc ánh xạ Lớp (Grade) & Vòng (Round)
     * 1. Lớp 1: Vòng 1-6 (round 1), Vòng 7-10 (round 2), Vòng 11-14 (round 3), Vòng 15-18 (round 4)
     * 2. Lớp 2: Tương tự
     * 3. Lớp 3: Tương tự
     * 4. Lớp 4: Tương tự
     * 5. Lớp 5: Tương tự
     */
    fun getFileName(grade: Int, round: Int): String {
      val roundGroup = when (round) {
        in 1..6 -> 1
        in 7..10 -> 2
        in 11..14 -> 3
        in 15..18 -> 4
        else -> 1
      }
      val safeGrade = if (grade in 1..5) grade else 1
      return "grade_${safeGrade}_round_${roundGroup}.json"
    }

    /**
     * Trả về danh sách URL ứng viên (hỗ trợ cả dấu gạch dưới và khoảng cách URL encoding)
     */
    fun getCandidateUrls(grade: Int, round: Int): List<String> {
      val roundGroup = when (round) {
        in 1..6 -> 1
        in 7..10 -> 2
        in 11..14 -> 3
        in 15..18 -> 4
        else -> 1
      }
      val safeGrade = if (grade in 1..5) grade else 1
      return listOf(
        "${BASE_URL}grade_${safeGrade}_round_${roundGroup}.json",
        "${BASE_URL}grade%20${safeGrade}_round%20${roundGroup}.json",
        "${BASE_URL}grade%20${safeGrade}_round%20${roundGroup}",
        "${BASE_URL}grade_${safeGrade}_round_${roundGroup}"
      )
    }
  }

  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  private val cacheDir: File by lazy {
    File(context.filesDir, "github_questions").apply {
      if (!exists()) mkdirs()
    }
  }

  private fun getCacheFile(grade: Int, round: Int): File {
    val roundGroup = when (round) {
      in 1..6 -> 1
      in 7..10 -> 2
      in 11..14 -> 3
      in 15..18 -> 4
      else -> 1
    }
    return File(cacheDir, "github_g${grade}_r${roundGroup}.json")
  }

  /**
   * Lấy dữ liệu JSON câu hỏi đã cache cục bộ (Offline-First)
   */
  fun getCachedQuestionsJson(grade: Int, round: Int): String? {
    val file = getCacheFile(grade, round)
    return if (file.exists() && file.length() > 0) {
      try {
        file.readText()
      } catch (e: Exception) {
        Log.e(TAG, "Lỗi đọc file cache câu hỏi: ${e.message}")
        null
      }
    } else {
      null
    }
  }

  /**
   * Tải file JSON mới nhất từ GitHub repository và lưu vào bộ nhớ cục bộ
   */
  fun fetchAndCacheQuestions(
    grade: Int,
    round: Int,
    onSuccess: ((String) -> Unit)? = null,
    onError: ((String) -> Unit)? = null
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      val urls = getCandidateUrls(grade, round)
      var downloadedJson: String? = null
      var lastError = "Không thể kết nối mạng"

      for (url in urls) {
        try {
          val request = Request.Builder().url(url).build()
          val response = okHttpClient.newCall(request).execute()
          if (response.isSuccessful) {
            val bodyString = response.body?.string()
            if (isValidJson(bodyString)) {
              downloadedJson = bodyString
              Log.d(TAG, "Tải thành công JSON từ GitHub: $url")
              break
            }
          } else {
            lastError = "HTTP ${response.code} tại $url"
          }
        } catch (e: Exception) {
          lastError = e.message ?: "Lỗi kết nối"
        }
      }

      withContext(Dispatchers.Main) {
        if (downloadedJson != null) {
          try {
            val cacheFile = getCacheFile(grade, round)
            cacheFile.writeText(downloadedJson)
            Log.d(TAG, "Đã lưu thành công cache tại: ${cacheFile.absolutePath}")
            onSuccess?.invoke(downloadedJson)
          } catch (e: Exception) {
            Log.e(TAG, "Lỗi ghi file cache: ${e.message}")
            onSuccess?.invoke(downloadedJson)
          }
        } else {
          Log.w(TAG, "Không tải được JSON từ GitHub ($lastError). Sử dụng đề dự phòng.")
          onError?.invoke(lastError)
        }
      }
    }
  }

  /**
   * Tự động kiểm tra và đồng bộ câu hỏi 5 khối lớp x 4 nhóm vòng thi trong nền khi có mạng
   */
  fun syncAllGradesInBackground() {
    CoroutineScope(Dispatchers.IO).launch {
      for (grade in 1..5) {
        for (group in listOf(1, 7, 11, 15)) {
          val file = getCacheFile(grade, group)
          val isExpired = !file.exists() || (System.currentTimeMillis() - file.lastModified() > 86400000L)
          if (isExpired) {
            fetchAndCacheQuestions(grade, group)
          }
        }
      }
    }
  }

  private fun isValidJson(str: String?): Boolean {
    if (str.isNullOrBlank()) return false
    val trimmed = str.trim()
    return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
  }
}
