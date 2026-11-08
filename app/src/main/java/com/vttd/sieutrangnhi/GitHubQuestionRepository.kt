package com.vttd.sieutrangnhi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     * Quy đổi số Chặng (1..18) về đúng chỉ số Round (1..4):
     * - Chặng 1..6   -> round = 1
     * - Chặng 7..10  -> round = 2
     * - Chặng 11..14 -> round = 3
     * - Chặng 15..18 -> round = 4
     */
    fun mapChangToRound(chang: Int): Int {
      return when (chang) {
        in 1..6 -> 1
        in 7..10 -> 2
        in 11..14 -> 3
        in 15..18 -> 4
        else -> 1
      }
    }

    /**
     * Tương thích ngược với getRoundGroup
     */
    fun getRoundGroup(round: Int): Int = mapChangToRound(round)

    /**
     * Lấy đường dẫn Raw URL GitHub trực tiếp (Direct Mapping) theo Lớp (1..5) và Round (1..4)
     */
    fun getDirectRawUrl(grade: Int, changOrRound: Int): String {
      val safeGrade = if (grade in 1..5) grade else 1
      val round = mapChangToRound(changOrRound)
      return "https://raw.githubusercontent.com/junvu1707/Trangsinhi_de/refs/heads/main/grade%20${safeGrade}_round%20${round}.json"
    }

    /**
     * Tên file JSON tương ứng trong local assets
     */
    fun getLocalAssetFileName(grade: Int, changOrRound: Int): String {
      val safeGrade = if (grade in 1..5) grade else 1
      val round = mapChangToRound(changOrRound)
      return "grade_${safeGrade}_round_${round}.json"
    }
  }

  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

  private fun getCacheFile(grade: Int, changOrRound: Int): File {
    val safeGrade = if (grade in 1..5) grade else 1
    val round = mapChangToRound(changOrRound)
    return File(cacheDir, "github_g${safeGrade}_r${round}.json")
  }

  /**
   * Đọc dữ liệu từ local asset dự phòng (Ưu tiên 2)
   */
  fun getLocalAssetQuestionsJson(grade: Int, changOrRound: Int): String? {
    val fileName = getLocalAssetFileName(grade, changOrRound)
    return try {
      context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }.also {
        Log.d(TAG, "[Local Assets] Đã nạp thành công bộ đề dự phòng từ asset: $fileName")
      }
    } catch (e: Exception) {
      Log.w(TAG, "[Local Assets] Không thể đọc asset '$fileName': ${e.message}")
      null
    }
  }

  /**
   * Lấy câu hỏi từ Cache GitHub đã tải về (Ưu tiên 1)
   */
  fun getCachedQuestionsJson(grade: Int, changOrRound: Int): String? {
    val cacheFile = getCacheFile(grade, changOrRound)
    if (cacheFile.exists() && cacheFile.length() > 0) {
      try {
        val text = cacheFile.readText()
        if (isValidJson(text)) {
          Log.d(TAG, "[Ưu tiên 1 - GitHub] Sử dụng đề đã tải từ GitHub (cache): ${cacheFile.name}")
          return text
        }
      } catch (e: Exception) {
        Log.e(TAG, "[Ưu tiên 1 - GitHub] Lỗi đọc cache ${cacheFile.name}: ${e.message}")
      }
    }
    return null
  }

  /**
   * Tải file JSON mới nhất từ GitHub qua Direct Raw URL (Ưu tiên 1)
   */
  fun fetchAndCacheQuestions(
    grade: Int,
    changOrRound: Int,
    onSuccess: ((String) -> Unit)? = null,
    onError: ((String) -> Unit)? = null
  ) {
    repositoryScope.launch {
      val url = getDirectRawUrl(grade, changOrRound)
      var downloadedJson: String? = null
      var lastError = "Không thể kết nối mạng"

      try {
        Log.d(TAG, "[GitHub] Đang fetch đề thi từ: $url")
        val request = Request.Builder()
          .header("User-Agent", "TNTV-AndroidApp")
          .url(url)
          .build()
        okHttpClient.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val bodyString = response.body?.string()
            if (isValidJson(bodyString)) {
              downloadedJson = bodyString
              Log.d(TAG, "[GitHub] Tải và parse JSON thành công từ GitHub: $url")
            } else {
              lastError = "Dữ liệu trả về không phải JSON hợp lệ"
              Log.w(TAG, "[GitHub] $lastError từ $url")
            }
          } else {
            lastError = "HTTP ${response.code} tại $url"
            Log.w(TAG, "[GitHub] Không tải được từ GitHub: $lastError")
          }
        }
      } catch (e: Exception) {
        lastError = e.message ?: "Lỗi kết nối"
        Log.w(TAG, "[GitHub] Ngoại lệ khi tải từ GitHub: $lastError")
      }

      val jsonToSave = downloadedJson
      withContext(Dispatchers.Main) {
        if (jsonToSave != null) {
          try {
            val cacheFile = getCacheFile(grade, changOrRound)
            cacheFile.writeText(jsonToSave)
            Log.d(TAG, "[GitHub] Đã ghi cache thành công tại: ${cacheFile.absolutePath}")
            onSuccess?.invoke(jsonToSave)
          } catch (e: Exception) {
            Log.e(TAG, "[GitHub] Lỗi ghi file cache: ${e.message}")
            onSuccess?.invoke(jsonToSave)
          }
        } else {
          Log.w(TAG, "[GitHub] Fetch GitHub thất bại ($lastError). Chuyển sang ƯU TIÊN 2 (Local JSON Assets).")
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
    repositoryScope.launch {
      syncRepoQuestionsInternal(forceRefresh = false)
    }
  }

  /**
   * Ép buộc hoặc chủ động đồng bộ đề thi mới nhất từ GitHub Repository
   */
  fun syncRepoQuestions(forceRefresh: Boolean = true, onComplete: ((Boolean, String) -> Unit)? = null) {
    repositoryScope.launch {
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

        // Quét và tải trực tiếp 20 bộ đề chuẩn theo Direct Raw URL (5 Lớp x 4 Round)
        for (g in 1..5) {
          for (r in 1..4) {
            val url = getDirectRawUrl(g, r)
            val jsonStr = downloadRawJson(url)
            if (isValidJson(jsonStr)) {
              getCacheFile(g, r).writeText(jsonStr!!)
              updatedCount++
              Log.d(TAG, "[GitHub] Đồng bộ thành công đề Lớp $g Round $r từ GitHub")
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
      okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val body = response.body?.string() ?: ""
          if (body.startsWith("[")) {
            val jsonArr = JSONArray(body)
            if (jsonArr.length() > 0) {
              jsonArr.getJSONObject(0).optString("sha", "")
            } else ""
          } else ""
        } else ""
      }
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
      okHttpClient.newCall(request).execute().use { response ->
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
      okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val text = response.body?.string()
          if (isValidJson(text)) text else null
        } else null
      }
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
