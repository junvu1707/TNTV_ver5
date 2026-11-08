package com.vttd.sieutrangnhi

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.Random

/**
 * Mô hình câu hỏi chuẩn Trạng Nguyên Tiếng Việt
 */
data class QuestionModel(
  val id: String = "",
  val type: String = "TN", // TN, DT, NOI, CHON, SX
  val grade: String = "",
  val level: String = "",
  val round: Int = 0,
  val title: String = "",
  val question: String = "",
  val options: List<String> = emptyList(),
  val correctIndex: Int = 0,
  val answer: String = "",
  val explain: String = "",
  val imageKey: String = "",
  val words: List<String> = emptyList(),
  val elements: List<String> = emptyList(),
  val rawJson: JSONObject? = null
) {
  fun toJson(): JSONObject {
    val obj = if (rawJson != null) JSONObject(rawJson.toString()) else JSONObject()
    obj.put("id", id)
    obj.put("type", type)
    obj.put("title", title)
    obj.put("question", question)
    obj.put("options", JSONArray(options))
    obj.put("correctIndex", correctIndex)
    if (rawJson != null && rawJson.has("answer") && rawJson.optJSONObject("answer") != null) {
      obj.put("answer", rawJson.getJSONObject("answer"))
    } else if (answer.isNotBlank() || !obj.has("answer")) {
      obj.put("answer", answer)
    }
    obj.put("explain", explain)
    if (imageKey.isNotBlank()) obj.put("imageKey", imageKey)
    if (words.isNotEmpty()) obj.put("words", JSONArray(words))
    if (elements.isNotEmpty()) obj.put("elements", JSONArray(elements))
    return obj
  }

  companion object {
    fun fromJson(json: JSONObject): QuestionModel {
      val rawId = json.optString("questionId").ifBlank { json.optString("id") }
      var rawType = json.optString("type", "TN")
      if (rawType.isBlank() || rawType.equals("TN", ignoreCase = true)) {
        if (rawId.contains("noi", ignoreCase = true)) {
          rawType = "NOI"
        } else if (rawId.contains("chon", ignoreCase = true)) {
          rawType = "CHON"
        } else if (rawId.contains("sx", ignoreCase = true) || rawId.contains("sapxep", ignoreCase = true)) {
          rawType = "SX"
        } else if (rawId.contains("dt", ignoreCase = true) || rawId.contains("dien", ignoreCase = true)) {
          rawType = "DT"
        }
      }
      val gradeStr = json.optString("grade", "")
      val levelStr = json.optString("level", "")
      val roundVal = json.optInt("round", 0)

      val optsList = mutableListOf<String>()
      val optsArray = json.optJSONArray("options")
      if (optsArray != null) {
        for (i in 0 until optsArray.length()) {
          optsList.add(optsArray.optString(i, ""))
        }
      } else {
        val optsObj = json.optJSONObject("options")
        if (optsObj != null) {
          listOf("A", "B", "C", "D").forEach { k ->
            if (optsObj.has(k)) optsList.add(optsObj.optString(k, ""))
          }
        }
      }

      val wordsList = mutableListOf<String>()
      val wordsArray = json.optJSONArray("words")
      if (wordsArray != null) {
        for (i in 0 until wordsArray.length()) {
          wordsList.add(wordsArray.optString(i, ""))
        }
      } else {
        val ansObj = json.optJSONObject("answer")
        if (ansObj != null) {
          val keys = ansObj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val arr = ansObj.optJSONArray(k)
            if (arr != null) {
              for (i in 0 until arr.length()) {
                val w = arr.optString(i, "")
                if (w.isNotBlank()) wordsList.add(w)
              }
            }
          }
        }
      }

      val elemList = mutableListOf<String>()
      val elemArray = json.optJSONArray("elements")
      if (elemArray != null) {
        for (i in 0 until elemArray.length()) {
          elemList.add(elemArray.optString(i, ""))
        }
      }

      val ans = json.optString("answer").ifBlank { json.optString("blankAnswer") }
      var correctIdx = json.optInt("correctIndex", -1)
      if (correctIdx == -1 && optsList.isNotEmpty()) {
        val ansUpper = ans.trim().uppercase(Locale.ROOT)
        correctIdx = when (ansUpper) {
          "A" -> 0
          "B" -> 1
          "C" -> 2
          "D" -> 3
          else -> optsList.indexOf(ans).coerceAtLeast(0)
        }
      } else if (correctIdx < 0) {
        correctIdx = 0
      }

      val exp = json.optString("explanation").ifBlank { json.optString("explain") }
      val finalTitle = when {
        rawType.equals("SX", ignoreCase = true) -> "Sắp xếp câu"
        rawType.equals("NOI", ignoreCase = true) || rawId.contains("noi", ignoreCase = true) -> json.optString("title").ifBlank { "Nối cột tương ứng" }
        else -> json.optString("title")
      }
      val finalQuestion = if (rawType.equals("SX", ignoreCase = true)) "Sắp xếp thành câu" else json.optString("question")

      return QuestionModel(
        id = rawId,
        type = rawType,
        grade = gradeStr,
        level = levelStr,
        round = roundVal,
        title = finalTitle,
        question = finalQuestion,
        options = optsList,
        correctIndex = correctIdx,
        answer = ans,
        explain = exp,
        imageKey = json.optString("imageKey"),
        words = wordsList,
        elements = elemList,
        rawJson = json
      )
    }
  }
}

/**
 * Trình sinh đề thi ngẫu nhiên động 30 câu chuẩn cơ cấu:
 * - TN (Trắc nghiệm): 17 câu (~56.67%)
 * - DT (Điền từ): 7 câu (~23.33%)
 * - NOI (Nối): 2 câu (~6.67%)
 * - CHON (Chọn): 2 câu (~6.67%)
 * - SX (Sắp xếp): 2 câu (~6.67%)
 * Thứ tự ưu tiên nguồn đề: GitHub JSON -> Local Assets -> index.html (testmode)
 */
class DynamicExamGenerator(
  private val githubRepo: GitHubQuestionRepository,
) {

  companion object {
    private const val TAG = "DynamicExamGenerator"
    private val NORMALIZE_REGEX = Regex("[^a-z0-9àáảãạăắằẳẵặâấầnẩẫậnèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ]")
    private val DIGIT_REGEX = Regex("\\d+")
    private val ID_PREFIX_REGEX = Regex("^k(\\d+)_c(\\d+)", RegexOption.IGNORE_CASE)

    // Cơ cấu số lượng chuẩn cho bộ 30 câu hỏi
    val TARGET_QUOTAS = mapOf(
      "TN" to 17,
      "DT" to 7,
      "NOI" to 2,
      "CHON" to 2,
      "SX" to 2
    )

    /**
     * Quy đổi số Chặng (1..18) sang Round (1..4):
     * - Chặng 1..6: Round 1 (Cấp 1 - Vòng loại)
     * - Chặng 7..10: Round 2 (Cấp 2 - Vòng trường)
     * - Chặng 11..14: Round 3 (Cấp 3 - Vòng huyện/hương)
     * - Chặng 15..18: Round 4 (Cấp 4 - Vòng tỉnh/quốc gia)
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
     * Chuẩn hóa dạng câu hỏi về 5 loại chính: TN, DT, NOI, CHON, SX
     */
    fun standardizeType(typeStr: String?): String {
      if (typeStr.isNullOrBlank()) return "TN"
      val upper = typeStr.trim().uppercase(Locale.ROOT)
      return when {
        upper == "TN" || upper == "MCQ" || upper == "TRAC_NGHIEM" || upper == "DO_VUI" || upper == "NHIN_HINH" || upper == "READING" -> "TN"
        upper == "DT" || upper == "FILL" || upper == "DIEN_TU" || upper == "DIEN_KHUYET" -> "DT"
        upper == "NOI" || upper == "MATCH" || upper == "MATCH_MINI" || upper == "GHEP_DOI" -> "NOI"
        upper == "CHON" || upper == "CLASSIFY" || upper == "CLASSIFY_MINI" || upper == "PHAN_LOAI" -> "CHON"
        upper == "SX" || upper == "SAP_XEP" || upper == "SORT" || upper == "ORDER" -> "SX"
        else -> "TN"
      }
    }
  }

  // In-Memory RAM Cache tránh parse lại JSON lớn nhiều lần trong phiên làm việc
  private val questionPoolCache = java.util.concurrent.ConcurrentHashMap<String, List<QuestionModel>>()

  private fun normalizeQuestionKey(q: QuestionModel): String {
    if (q.id.isNotBlank()) return q.id.lowercase(Locale.ROOT).trim()
    val rawText = q.question.ifBlank { q.title }
    return rawText.lowercase(Locale.ROOT)
      .replace(NORMALIZE_REGEX, "")
      .trim()
  }

  /**
   * Kiểm tra lọc chuẩn xác khối lớp (grade) và vòng thi (round)
   */
  fun matchesGradeAndRound(q: QuestionModel, targetGrade: Int, targetRound: Int): Boolean {
    // 1. Kiểm tra grade nếu câu hỏi có trường grade
    if (q.grade.isNotBlank()) {
      val gradeDigits = DIGIT_REGEX.find(q.grade)?.value?.toIntOrNull()
      if (gradeDigits != null && gradeDigits != targetGrade) return false
    }

    // 2. Kiểm tra round nếu có
    if (q.round > 0 && q.round != targetRound) return false

    // 3. Kiểm tra level (Vòng thi)
    if (q.level.isNotBlank()) {
      val lvl = q.level.uppercase(Locale.ROOT)
      val roundFromLevel = when {
        lvl.contains("CAP_1") || lvl.contains("VONG_LOAI") || lvl.contains("ROUND_1") || lvl.contains("ROUND 1") -> 1
        lvl.contains("CAP_2") || lvl.contains("VONG_TRUONG") || lvl.contains("ROUND_2") || lvl.contains("ROUND 2") -> 2
        lvl.contains("CAP_3") || lvl.contains("VONG_HUONG") || lvl.contains("VONG_PHUONG") || lvl.contains("ROUND_3") || lvl.contains("ROUND 3") -> 3
        lvl.contains("CAP_4") || lvl.contains("CAP_5") || lvl.contains("VONG_TINH") || lvl.contains("VONG_QUOC_GIA") || lvl.contains("ROUND_4") || lvl.contains("ROUND 4") -> 4
        else -> null
      }
      if (roundFromLevel != null && roundFromLevel != targetRound) return false
    }

    // 4. Kiểm tra ID prefix như k1_c1, k2_c3
    if (q.id.isNotBlank()) {
      val match = ID_PREFIX_REGEX.find(q.id)
      if (match != null) {
        val (kStr, cStr) = match.destructured
        val kVal = kStr.toIntOrNull()
        val cVal = cStr.toIntOrNull()
        if (kVal != null && kVal != targetGrade) return false
        if (cVal != null && cVal != targetRound && !(targetRound == 4 && cVal == 5)) return false
      }
    }

    return true
  }

  /**
   * Tải pool câu hỏi tuân thủ tuyệt đối 3 cấp ưu tiên:
   * Ưu tiên 1: Đề trên GitHub (lấy luôn mỗi khi bật app)
   * Ưu tiên 2: Nếu không có mạng, không load được đề từ GitHub thì lấy đề ở file JSON trong assets
   * Ưu tiên 3: Nếu 2 phương án trên không được mới lấy đề ở index.html
   */
  fun loadQuestionPool(grade: Int, changOrRound: Int, localQuestionsJsonStr: String = ""): List<QuestionModel> {
    val round = mapChangToRound(changOrRound)
    val cacheKey = "g${grade}_r${round}"

    // Kiểm tra In-Memory RAM Cache trước
    questionPoolCache[cacheKey]?.let { cached ->
      if (cached.isNotEmpty()) {
        Log.d(TAG, "[RAM Cache] Đã lấy nhanh ${cached.size} câu từ In-Memory Cache (Lớp $grade - Round $round)")
        return cached
      }
    }

    val pool = mutableListOf<QuestionModel>()

    // --- ƯU TIÊN 1: ĐỀ TRÊN GITHUB ---
    val cachedGithubJson = githubRepo.getCachedQuestionsJson(grade, round)
    if (!cachedGithubJson.isNullOrBlank()) {
      val githubQuestions = parseQuestionsFromJsonString(cachedGithubJson)
        .filter { matchesGradeAndRound(it, grade, round) }
      if (githubQuestions.isNotEmpty()) {
        pool.addAll(githubQuestions)
        Log.d(TAG, "[Ưu tiên 1] Đã nạp thành công ${githubQuestions.size} câu từ GitHub (Lớp $grade - Round $round)")
      }
    }

    // Luôn kích hoạt fetch ngầm để cập nhật bản mới nhất từ GitHub
    githubRepo.fetchAndCacheQuestions(grade, round)

    // --- ƯU TIÊN 2: FILE JSON TRONG ASSETS (Khi không có mạng / chưa load được từ GitHub) ---
    if (pool.isEmpty()) {
      val assetJson = githubRepo.getLocalAssetQuestionsJson(grade, round)
      if (!assetJson.isNullOrBlank()) {
        val assetQuestions = parseQuestionsFromJsonString(assetJson)
          .filter { matchesGradeAndRound(it, grade, round) }
        if (assetQuestions.isNotEmpty()) {
          pool.addAll(assetQuestions)
          Log.d(TAG, "[Ưu tiên 2] Đã nạp thành công ${assetQuestions.size} câu từ Local Assets JSON")
        }
      }
    }

    // --- ƯU TIÊN 3: DỰ PHÒNG TỪ index.html (Nếu 2 phương án trên đều không được) ---
    if (pool.isEmpty() && localQuestionsJsonStr.isNotBlank()) {
      val localQuestions = parseQuestionsFromJsonString(localQuestionsJsonStr)
        .filter { matchesGradeAndRound(it, grade, round) }
      if (localQuestions.isNotEmpty()) {
        pool.addAll(localQuestions)
        Log.d(TAG, "[Ưu tiên 3] Đã nạp ${localQuestions.size} câu dự phòng từ index.html testmode")
      }
    }

    val distinctPool = pool.distinctBy { normalizeQuestionKey(it) }
    if (distinctPool.isNotEmpty()) {
      questionPoolCache[cacheKey] = distinctPool
    }
    return distinctPool
  }

  /**
   * Sinh bộ 30 câu hỏi phân bổ đúng tỷ lệ:
   * TN: 17, DT: 7, NOI: 2, CHON: 2, SX: 2
   * Xử lý bù trừ linh hoạt nếu một loại thiếu dữ liệu.
   */
  fun generateDynamicMode2Exam(
    grade: Int,
    changOrRound: Int,
    seed: Int = 1,
    localQuestionsJsonStr: String = "",
    targetCount: Int = 30
  ): String {
    val round = mapChangToRound(changOrRound)
    val safeGrade = if (grade in 1..5) grade else 1
    val seedVal = if (seed > 0) seed else ((System.currentTimeMillis() % 1000) + 1).toInt()
    val examId = "Chặng ${changOrRound}_$seedVal"

    // Seed PRNG kết hợp grade, round, chang và seedVal
    val prngSeed = (safeGrade.toLong() * 1000000007L) xor
                   (round.toLong() * 999999937L) xor
                   (changOrRound.toLong() * 65537L) xor
                   (seedVal.toLong() * 31337L) xor
                   0x5DEECE66DL
    val rng = Random(prngSeed)

    val pool = loadQuestionPool(safeGrade, changOrRound, localQuestionsJsonStr)
    Log.d(TAG, "Tổng số câu trong pool hợp lệ: ${pool.size} (Lớp $safeGrade - Chặng $changOrRound / Round $round)")

    // Phân nhóm câu hỏi theo 5 dạng chuẩn
    val grouped = pool.groupBy { standardizeType(it.type) }

    val selected = mutableListOf<QuestionModel>()
    val overflow = mutableListOf<QuestionModel>()
    val actualCounts = mutableMapOf<String, Int>()

    // Bốc câu hỏi theo đúng tỷ lệ định mức
    TARGET_QUOTAS.forEach { (type, quota) ->
      val list = (grouped[type] ?: emptyList()).shuffled(rng)
      val takeCount = minOf(quota, list.size)
      selected.addAll(list.take(takeCount))
      actualCounts[type] = takeCount
      if (list.size > takeCount) {
        overflow.addAll(list.drop(takeCount))
      }
    }

    // Bù số lượng còn thiếu từ các loại khác trong overflow nếu một loại thiếu
    if (selected.size < targetCount && overflow.isNotEmpty()) {
      val needed = targetCount - selected.size
      val fillList = overflow.shuffled(rng).take(needed)
      fillList.forEach { q ->
        val t = standardizeType(q.type)
        actualCounts[t] = (actualCounts[t] ?: 0) + 1
      }
      selected.addAll(fillList)
    }

    // Xáo trộn thứ tự các câu hỏi để các dạng xuất hiện đan xen tự nhiên
    val shuffledExam = selected.shuffled(rng)

    // Xáo trộn các phương án A, B, C, D của các câu trắc nghiệm
    val finalQuestions = shuffledExam.mapIndexed { idx, q ->
      shuffleQuestionOptions(q, rng, idx)
    }

    val jsonArray = JSONArray()
    finalQuestions.forEach { jsonArray.put(it.toJson()) }

    val countsObj = JSONObject()
    actualCounts.forEach { (k, v) -> countsObj.put(k, v) }

    val resultJson = JSONObject().apply {
      put("grade", safeGrade)
      put("round", round)
      put("chang", changOrRound)
      put("seed", seedVal)
      put("examId", examId)
      put("mode", 2)
      put("timerSeconds", 1500)
      put("totalQuestions", finalQuestions.size)
      put("typeDistribution", countsObj)
      put("questions", jsonArray)
    }

    return resultJson.toString()
  }

  fun parseQuestionsFromJsonString(jsonStr: String): List<QuestionModel> {
    val list = mutableListOf<QuestionModel>()
    try {
      val trimmed = jsonStr.trim()
      if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i)
          if (obj != null) list.add(QuestionModel.fromJson(obj))
        }
      } else if (trimmed.startsWith("{")) {
        val root = JSONObject(trimmed)
        val arr = root.optJSONArray("questions")
        if (arr != null) {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj != null) list.add(QuestionModel.fromJson(obj))
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi parse JSON câu hỏi: ${e.message}")
    }
    return list
  }

  private fun shuffleQuestionOptions(q: QuestionModel, rng: Random, qIndex: Int): QuestionModel {
    if (q.options.size < 2 || standardizeType(q.type) != "TN") return q

    val correctText = if (q.correctIndex in q.options.indices) {
      q.options[q.correctIndex]
    } else if (q.answer.isNotBlank()) {
      q.answer
    } else {
      q.options[0]
    }

    val shuffled = q.options.shuffled(rng).toMutableList()
    var newIdx = shuffled.indexOf(correctText)
    if (newIdx == -1) {
      shuffled[0] = correctText
      newIdx = 0
    }

    // Tránh đáp án đúng rơi liên tục vào một vị trí cố định
    val offset = (qIndex % (shuffled.size - 1)) + 1
    val targetIdx = (newIdx + offset) % shuffled.size
    val temp = shuffled[newIdx]
    shuffled[newIdx] = shuffled[targetIdx]
    shuffled[targetIdx] = temp
    newIdx = targetIdx

    return q.copy(options = shuffled, correctIndex = newIdx)
  }
}
