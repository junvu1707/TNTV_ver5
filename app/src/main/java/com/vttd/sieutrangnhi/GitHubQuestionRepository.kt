package com.vttd.sieutrangnhi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Quản lý tự động tải, đồng bộ & cache file JSON chứa câu hỏi từ GitHub Repository.
 * Repository: https://github.com/junvu1707/Trangsinhi_de
 * Cấu trúc file đề: grade_<grade>_round_<round> (ví dụ: grade_1_round_1.json tương đương Lớp 1 - Chặng 1)
 *
 * Thiết kế theo mô hình Offline-First:
 * - Đọc tức thì từ cache cục bộ (Storage/File) nếu thiết bị không có internet.
 * - Tự động kiểm tra commit SHA & đồng bộ khi GitHub repo có nội dung mới.
 */
class GitHubQuestionRepository(private val context: Context) {

  companion object {
    private const val TAG = "GitHubQuestionRepo"
    private const val BASE_URL = "https://raw.githubusercontent.com/junvu1707/Trangsinhi_de/main/"
    private const val COMMITS_API_URL = "https://api.github.com/repos/junvu1707/Trangsinhi_de/commits?per_page=1"
    private const val CONTENTS_API_URL = "https://api.github.com/repos/junvu1707/Trangsinhi_de/contents"

    private const val PREFS_NAME = "github_question_sync_prefs"
    private const val KEY_LAST_COMMIT_SHA = "last_commit_sha"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    /**
     * Quy đổi số Vòng (Round 1..18) ra nhóm Chặng tương ứng (1..4)
     * - Chặng 1 (Round 1): Vòng 1-6 (Chú Ong Chăm Chỉ)
     * - Chặng 2 (Round 2): Vòng 7-10 (Khu Vườn Kỳ Diệu)
     * - Chặng 3 (Round 3): Vòng 11-14 (Mê Cung Kỳ Bí)
     * - Chặng 4 (Round 4): Vòng 15-18 (Chinh Phục Đỉnh Cao)
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
     * Xác định tên file JSON chuẩn trên GitHub theo Lớp & Vòng
     */
    fun getFileName(grade: Int, round: Int): String {
      val safeGrade = if (grade in 1..5) grade else 1
      val group = getRoundGroup(round)
      return "grade_${safeGrade}_round_${group}.json"
    }

    /**
     * Trả về danh sách URL ứng viên linh hoạt cho một khối lớp & vòng/chặng
     * Hỗ trợ các kiểu đặt tên: grade_1_round_1.json, grade 1_round 1.json, grade_1_round_1, ...
     */
    fun getCandidateUrls(grade: Int, round: Int): List<String> {
      val group = getRoundGroup(round)
      val safeGrade = if (grade in 1..5) grade else 1

      val names = LinkedHashSet<String>().apply {
        add("grade_${safeGrade}_round_${group}.json")
        add("grade_${safeGrade}_round_${round}.json")
        add("grade_${safeGrade}_round_${group}")
        add("grade_${safeGrade}_round_${round}")
        add("grade%20${safeGrade}_round%20${group}.json")
        add("grade%20${safeGrade}_round%20${round}.json")
        add("grade%20${safeGrade}_round%20${group}")
        add("grade%20${safeGrade}_round%20${round}")
        add("grade${safeGrade}_round${group}.json")
        add("grade${safeGrade}_round${round}.json")
      }

      return names.map { "${BASE_URL}$it" }
    }
  }

  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

  private fun getCacheFileForSpecificRound(grade: Int, round: Int): File {
    return File(cacheDir, "github_g${grade}_r${round}.json")
  }

  private fun getCacheFileForGroup(grade: Int, round: Int): File {
    val group = getRoundGroup(round)
    return File(cacheDir, "github_g${grade}_r${group}.json")
  }

  /**
   * Lấy dữ liệu JSON câu hỏi đã cache cục bộ (Offline-First).
   * Ưu tiên file theo vòng cụ thể (r1..r18), nếu không có sẽ lấy theo file chặng group (r1..r4).
   */
  fun getCachedQuestionsJson(grade: Int, round: Int): String? {
    val specificFile = getCacheFileForSpecificRound(grade, round)
    if (specificFile.exists() && specificFile.length() > 0) {
      try {
        val text = specificFile.readText()
        if (isValidJson(text)) return text
      } catch (e: Exception) {
        Log.e(TAG, "Lỗi đọc cache specific g${grade} r${round}: ${e.message}")
      }
    }

    val groupFile = getCacheFileForGroup(grade, round)
    if (groupFile.exists() && groupFile.length() > 0) {
      try {
        val text = groupFile.readText()
        if (isValidJson(text)) return text
      } catch (e: Exception) {
        Log.e(TAG, "Lỗi đọc cache group g${grade} r${round}: ${e.message}")
      }
    }

    return null
  }

  /**
   * Tải file JSON mới nhất cho một lớp & vòng từ GitHub và lưu vào bộ nhớ cục bộ
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
          val request = Request.Builder()
            .header("User-Agent", "TNTV-AndroidApp")
            .url(url)
            .build()
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
            val specificFile = getCacheFileForSpecificRound(grade, round)
            val groupFile = getCacheFileForGroup(grade, round)
            specificFile.writeText(downloadedJson)
            groupFile.writeText(downloadedJson)
            Log.d(TAG, "Đã lưu thành công cache tại: ${specificFile.absolutePath}")
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
   * Tự động kiểm tra commit SHA trên GitHub. Nếu repo có cập nhật mới (commit SHA thay đổi):
   * Tự động quét và đồng bộ toàn bộ file đề thi của tất cả các khối lớp & chặng.
   */
  fun syncAllGradesInBackground() {
    CoroutineScope(Dispatchers.IO).launch {
      syncRepoQuestionsInternal(forceRefresh = false)
    }
  }

  /**
   * Ép buộc hoặc chủ động đồng bộ đề thi mới nhất từ GitHub Repository
   */
  fun syncRepoQuestions(forceRefresh: Boolean = true, onComplete: ((Boolean, String) -> Unit)? = null) {
    CoroutineScope(Dispatchers.IO).launch {
      val (success, message) = syncRepoQuestionsInternal(forceRefresh)
      withContext(Dispatchers.Main) {
        onComplete?.invoke(success, message)
      }
    }
  }

  private suspend fun syncRepoQuestionsInternal(forceRefresh: Boolean): Pair<Boolean, String> {
    return withContext(Dispatchers.IO) {
      try {
        val latestSha = fetchLatestCommitSha()
        val storedSha = prefs.getString(KEY_LAST_COMMIT_SHA, "") ?: ""
        val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        val timeDiff = System.currentTimeMillis() - lastSyncTime

        val hasNewCommit = latestSha.isNotBlank() && latestSha != storedSha
        val isExpired = timeDiff > (6 * 3600 * 1000L) // 6 tiếng

        if (!forceRefresh && !hasNewCommit && !isExpired && hasAnyCacheFile()) {
          Log.d(TAG, "Đề thi trên GitHub đã ở phiên bản mới nhất (Commit: $latestSha)")
          return@withContext Pair(true, "Ngân hàng đề thi trên GitHub đã ở phiên bản mới nhất!")
        }

        Log.d(TAG, "Phát hiện cập nhật mới trên GitHub (SHA mới: $latestSha, SHA cũ: $storedSha). Tiến hành đồng bộ...")

        var updatedCount = 0

        // Cách 1: Thử quét danh sách file trực tiếp từ GitHub API Contents
        val fileMap = fetchDirectoryFilesFromGitHubApi()
        if (fileMap.isNotEmpty()) {
          val regex = Regex("""grade[_\s]*(\d+)[_\s]*round[_\s]*(\d+)""", RegexOption.IGNORE_CASE)
          for ((fileName, downloadUrl) in fileMap) {
            val match = regex.find(fileName)
            if (match != null) {
              val g = match.groupValues[1].toIntOrNull() ?: 1
              val r = match.groupValues[2].toIntOrNull() ?: 1
              val jsonContent = downloadRawJson(downloadUrl)
              if (isValidJson(jsonContent)) {
                getCacheFileForSpecificRound(g, r).writeText(jsonContent!!)
                getCacheFileForGroup(g, r).writeText(jsonContent)
                updatedCount++
                Log.d(TAG, "Đã cập nhật file: $fileName (Lớp $g, Vòng/Chặng $r)")
              }
            }
          }
        }

        // Cách 2: Nếu API không trả về file hoặc bị giới hạn rate limit, quét dự phòng theo candidate URLs
        if (updatedCount == 0) {
          for (grade in 1..5) {
            for (roundGroup in 1..4) {
              val urls = getCandidateUrls(grade, roundGroup)
              for (url in urls) {
                val jsonStr = downloadRawJson(url)
                if (isValidJson(jsonStr)) {
                  getCacheFileForSpecificRound(grade, roundGroup).writeText(jsonStr!!)
                  getCacheFileForGroup(grade, roundGroup).writeText(jsonStr)
                  updatedCount++
                  break
                }
              }
            }
          }
        }

        if (latestSha.isNotBlank()) {
          prefs.edit {
            putString(KEY_LAST_COMMIT_SHA, latestSha)
            putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
          }
        }

        if (updatedCount > 0) {
          Log.d(TAG, "Đã đồng bộ thành công $updatedCount file đề thi từ GitHub!")
          Pair(true, "Đã tự động cập nhật $updatedCount bộ đề mới nhất từ GitHub!")
        } else {
          Pair(true, "Đồng bộ hoàn tất. Dữ liệu đề thi đã sẵn sàng.")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Lỗi đồng bộ câu hỏi GitHub: ${e.message}")
        Pair(false, "Không thể kết nối tới GitHub repo: ${e.message}")
      }
    }
  }

  private fun fetchLatestCommitSha(): String {
    return try {
      val request = Request.Builder()
        .url(COMMITS_API_URL)
        .header("User-Agent", "TNTV-AndroidApp")
        .build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val body = response.body?.string() ?: ""
        if (body.startsWith("[")) {
          val jsonArr = JSONArray(body)
          if (jsonArr.length() > 0) {
            jsonArr.getJSONObject(0).optString("sha", "")
          } else ""
        } else ""
      } else ""
    } catch (e: Exception) {
      Log.w(TAG, "Không thể kiểm tra commit SHA: ${e.message}")
      ""
    }
  }

  private fun fetchDirectoryFilesFromGitHubApi(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    try {
      val request = Request.Builder()
        .url(CONTENTS_API_URL)
        .header("User-Agent", "TNTV-AndroidApp")
        .build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val body = response.body?.string() ?: ""
        if (body.startsWith("[")) {
          val jsonArr = JSONArray(body)
          for (i in 0 until jsonArr.length()) {
            val item = jsonArr.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val downloadUrl = item.optString("download_url", "")
            if (name.isNotBlank() && downloadUrl.isNotBlank()) {
              result[name] = downloadUrl
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Không thể danh sách file từ GitHub API: ${e.message}")
    }
    return result
  }

  private fun downloadRawJson(url: String): String? {
    return try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "TNTV-AndroidApp")
        .build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val text = response.body?.string()
        if (isValidJson(text)) text else null
      } else null
    } catch (_: Exception) {
      null
    }
  }

  private fun hasAnyCacheFile(): Boolean {
    val files = cacheDir.listFiles()
    return files != null && files.any { it.isFile && it.length() > 0 }
  }

  private fun isValidJson(str: String?): Boolean {
    if (str.isNullOrBlank()) return false
    val trimmed = str.trim()
    return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
  }
}
