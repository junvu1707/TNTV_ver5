package com.vttd.sieutrangnhi

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.Random

/**
 * Data Model cho một câu hỏi thi chuẩn Trạng Nguyên Tiếng Việt
 */
data class QuestionModel(
  val id: String = "",
  val type: String = "MCQ", // MCQ, DT, SAP_XEP, DO_VUI, NHIN_HINH
  val title: String = "",
  val question: String = "",
  val options: List<String> = emptyList(),
  val correctIndex: Int = 0,
  val answer: String = "",
  val explain: String = "",
  val imageKey: String = "",
  val words: List<String> = emptyList(),
) {
  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("id", id)
      put("type", type)
      put("title", title)
      put("question", question)
      put("options", JSONArray(options))
      put("correctIndex", correctIndex)
      put("answer", answer)
      put("explain", explain)
      put("imageKey", imageKey)
      put("words", JSONArray(words))
    }
  }

  companion object {
    fun fromJson(json: JSONObject): QuestionModel {
      val optsList = mutableListOf<String>()
      val optsArray = json.optJSONArray("options")
      if (optsArray != null) {
        for (i in 0 until optsArray.length()) {
          optsList.add(optsArray.optString(i, ""))
        }
      }

      val wordsList = mutableListOf<String>()
      val wordsArray = json.optJSONArray("words")
      if (wordsArray != null) {
        for (i in 0 until wordsArray.length()) {
          wordsList.add(wordsArray.optString(i, ""))
        }
      }

      return QuestionModel(
        id = json.optString("id", ""),
        type = json.optString("type", "MCQ"),
        title = json.optString("title", ""),
        question = json.optString("question", ""),
        options = optsList,
        correctIndex = json.optInt("correctIndex", 0),
        answer = json.optString("answer", ""),
        explain = json.optString("explain", ""),
        imageKey = json.optString("imageKey", ""),
        words = wordsList,
      )
    }
  }
}

/**
 * Trình sinh đề thi ngẫu nhiên động 1000 bộ đề cho Vòng 4 - 18
 * - Tự động gom và trộn toàn bộ câu hỏi từ 2 Nguồn (Local Storage + GitHub JSON)
 * - Tạo Mã ID Đề ("Chặng 4_999", "Chặng 7_123")
 * - Lọc trùng tuyệt đối bằng Kotlin Collections (.distinctBy)
 */
class DynamicExamGenerator(
  private val githubRepo: GitHubQuestionRepository,
) {

  companion object {
    private const val TAG = "DynamicExamGenerator"
  }

  private fun normalizeQuestionKey(q: QuestionModel): String {
    if (q.id.isNotBlank()) return q.id.lowercase(Locale.ROOT).trim()
    val rawText = q.question.ifBlank { q.title }
    return rawText.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z0-9àáảãạăắằẳẵặâấầnẩẫậnèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ]"), "")
      .trim()
  }

  fun generateDynamicMode2Exam(
    grade: Int,
    round: Int,
    seed: Int = 1,
    localQuestionsJsonStr: String = "",
    targetCount: Int = 30
  ): String {
    val pool = mutableListOf<QuestionModel>()

    if (localQuestionsJsonStr.isNotBlank()) {
      val localQuestions = parseQuestionsFromJsonString(localQuestionsJsonStr)
      pool.addAll(localQuestions)
      Log.d(TAG, "Nạp ${localQuestions.size} câu từ Nguồn Local Storage")
    }

    val cachedGithubJson = githubRepo.getCachedQuestionsJson(grade, round)
    if (!cachedGithubJson.isNullOrBlank()) {
      val githubQuestions = parseQuestionsFromJsonString(cachedGithubJson)
      pool.addAll(githubQuestions)
      Log.d(TAG, "Nạp ${githubQuestions.size} câu từ Nguồn GitHub JSON Cache")
    }

    githubRepo.fetchAndCacheQuestions(grade, round)

    val uniquePool = pool.distinctBy { normalizeQuestionKey(it) }

    val seedVal = if (seed in 1..1000) seed else ((System.currentTimeMillis() % 1000) + 1).toInt()
    val examId = "Chặng ${round}_$seedVal"

    val timeFactor = System.currentTimeMillis() / 15000L
    val prngSeed = (grade.toLong() * 100000000L) + (round.toLong() * 1000000L) + (seedVal.toLong() * 1003001L) + (timeFactor * 37L) + 1234567L
    val rng = Random(prngSeed)

    val shuffledPool = uniquePool.shuffled(rng)

    val selectedQuestions = if (shuffledPool.size >= targetCount) {
      shuffledPool.take(targetCount)
    } else {
      shuffledPool
    }

    val finalQuestions = shuffleQuestionOptions(selectedQuestions, rng)

    val jsonArray = JSONArray()
    finalQuestions.forEach { jsonArray.put(it.toJson()) }

    val resultJson = JSONObject().apply {
      put("grade", grade)
      put("round", round)
      put("seed", seedVal)
      put("examId", examId)
      put("mode", 2)
      put("timerSeconds", 1800)
      put("totalQuestions", finalQuestions.size)
      put("questions", jsonArray)
    }

    return resultJson.toString()
  }

  private fun parseQuestionsFromJsonString(jsonStr: String): List<QuestionModel> {
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

  private fun shuffleQuestionOptions(questions: List<QuestionModel>, rng: Random): List<QuestionModel> {
    var lastCorrectIndex = -1
    var repeatCount = 0

    return questions.mapIndexed { qIdx, q ->
      if (q.options.size < 2 || q.type == "DT") {
        q
      } else {
        val correctText = if (q.correctIndex in q.options.indices) {
          q.options[q.correctIndex]
        } else if (q.answer.isNotBlank()) {
          q.answer
        } else {
          q.options[0]
        }

        val shuffled = q.options.shuffled(rng).toMutableList()
        var newCorrectIndex = shuffled.indexOf(correctText)
        if (newCorrectIndex == -1) {
          shuffled[0] = correctText
          newCorrectIndex = 0
        }

        if (newCorrectIndex == lastCorrectIndex) {
          repeatCount++
        } else {
          repeatCount = 1
        }

        if (repeatCount >= 2 && shuffled.size >= 2) {
          val swapOffset = (qIdx % (shuffled.size - 1)) + 1
          val targetIndex = (newCorrectIndex + swapOffset) % shuffled.size

          val temp = shuffled[newCorrectIndex]
          shuffled[newCorrectIndex] = shuffled[targetIndex]
          shuffled[targetIndex] = temp

          newCorrectIndex = targetIndex
          repeatCount = 1
        }

        lastCorrectIndex = newCorrectIndex

        q.copy(options = shuffled, correctIndex = newCorrectIndex)
      }
    }
  }
}
