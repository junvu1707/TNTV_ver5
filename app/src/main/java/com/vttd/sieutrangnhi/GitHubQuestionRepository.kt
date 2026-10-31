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
 * Quản lý tự động tải, đồng bộ & cache file JSON chứa câu hỏi từ GitHub Repository:
 * Repository: https://github.com/junvu1707/Trangsinhi_de
 * Cấu trúc file: grade_[Lớp]_round_[Nhóm Chặng].json
 *
 * Ánh xạ Nhóm Chặng (Round Group):
 * - round 1: Lớp X - Chú Ong Chăm Chỉ (Chặng 1 - 6)
 * - round 2: Lớp X - Khu Vườn Kỳ Diệu (Chặng 7 - 10)
 * - round 3: Lớp X - Mê Cung Kỳ Bí (Chặng 11 - 14)
 * - round 4: Lớp X - Chinh Phục Đỉnh Cao (Chặng 15 - 18)
 */
class GitHubQuestionRepository(private val context: Context) {

  companion object {
    private const val TAG = "GitHubQuestionRepo"
    private const val BASE_URL = "https://raw.githubusercontent.com/junvu1707/Trangsinhi_de/main/"

    /**
     * Xác định Nhóm Vòng Thi từ số Chặng (1 - 18)
     */
    fun getRoundGroup(round: Int): Int {
      return when (round) {
        in 1..6 -> 1
        in 7..10 -> 2
        in 11..14 -> 3
        in 15..18 -> 4
        else -> 1
      }
    }

    /**
     * Xác định tên file JSON chuẩn trên GitHub
     */
    fun getFileName(grade: Int, round: Int): String {
      val group = getRoundGroup(round)
      val safeGrade = if (grade in 1..5) grade else 1
      return "grade_${safeGrade}_round_${group}.json"
    }

    /**
     * Trả về danh sách URL ứng viên (hỗ trợ cả dấu gạch dưới, khoảng cách URL encoding và thiếu đuôi .json)
     */
    fun getCandidateUrls(grade: Int, round: Int): List<String> {
      val group = getRoundGroup(round)
      val safeGrade = if (grade in 1..5) grade else 1
      return listOf(
        "${BASE_URL}grade_${safeGrade}_round_${group}.json",
        "${BASE_URL}grade%20${safeGrade}_round%20${group}.json",
        "${BASE_URL}grade%20${safeGrade}_round_${group}.json",
        "${BASE_URL}grade_${safeGrade}_round%20${group}.json",
        "${BASE_URL}grade${safeGrade}_round${group}.json",
        "${BASE_URL}grade_${safeGrade}_round_${group}",
        "${BASE_URL}grade%20${safeGrade}_round%20${group}"
      )
    }
  }

  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .build()
  }

  private val cacheDir: File by lazy {
    File(context.filesDir, "github_questions").apply {
      if (!exists()) mkdirs()
    }
  }

  private fun getCacheFile(grade: Int, round: Int): File {
    val group = getRoundGroup(round)
    return File(cacheDir, "github_g${grade}_r${group}.json")
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
   * Tự động kiểm tra và đồng bộ câu hỏi 5 khối lớp x 4 nhóm vòng thi trong nền khi ứng dụng khởi chạy
   */
  fun syncAllGradesInBackground() {
    CoroutineScope(Dispatchers.IO).launch {
      for (grade in 1..5) {
        for (roundGroup in listOf(1, 7, 11, 15)) {
          val file = getCacheFile(grade, roundGroup)
          val isExpired = !file.exists() || (System.currentTimeMillis() - file.lastModified() > 43200000L) // 12 giờ
          if (isExpired) {
            fetchAndCacheQuestions(grade, roundGroup)
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
