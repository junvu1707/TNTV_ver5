package com.sieutrangnhi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vttd.sieutrangnhi.DynamicExamGenerator
import com.vttd.sieutrangnhi.GitHubQuestionRepository
import com.vttd.sieutrangnhi.QuestionModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicExamGeneratorTest {

  private fun getGenerator(): DynamicExamGenerator {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = GitHubQuestionRepository(context)
    return DynamicExamGenerator(repo)
  }

  @Test
  fun testMapChangToRound() {
    for (c in 1..6) {
      assertEquals(1, DynamicExamGenerator.mapChangToRound(c))
    }
    for (c in 7..10) {
      assertEquals(2, DynamicExamGenerator.mapChangToRound(c))
    }
    for (c in 11..14) {
      assertEquals(3, DynamicExamGenerator.mapChangToRound(c))
    }
    for (c in 15..18) {
      assertEquals(4, DynamicExamGenerator.mapChangToRound(c))
    }
  }

  @Test
  fun testStandardizeType() {
    assertEquals("TN", DynamicExamGenerator.standardizeType("TN"))
    assertEquals("TN", DynamicExamGenerator.standardizeType("MCQ"))
    assertEquals("DT", DynamicExamGenerator.standardizeType("DT"))
    assertEquals("DT", DynamicExamGenerator.standardizeType("FILL"))
    assertEquals("NOI", DynamicExamGenerator.standardizeType("NOI"))
    assertEquals("NOI", DynamicExamGenerator.standardizeType("MATCH"))
    assertEquals("CHON", DynamicExamGenerator.standardizeType("CHON"))
    assertEquals("CHON", DynamicExamGenerator.standardizeType("CLASSIFY_MINI"))
    assertEquals("SX", DynamicExamGenerator.standardizeType("SX"))
    assertEquals("SX", DynamicExamGenerator.standardizeType("SAP_XEP"))
  }

  @Test
  fun testMatchesGradeAndRound() {
    val generator = getGenerator()

    val qCorrect = QuestionModel(
      id = "k2_c2_tn_001",
      grade = "KHOI_2",
      level = "CAP_2_VONG_TRUONG",
      type = "TN"
    )
    assertTrue(generator.matchesGradeAndRound(qCorrect, targetGrade = 2, targetRound = 2))
    assertFalse(generator.matchesGradeAndRound(qCorrect, targetGrade = 1, targetRound = 2))
    assertFalse(generator.matchesGradeAndRound(qCorrect, targetGrade = 2, targetRound = 1))

    val qWrongLevel = QuestionModel(
      id = "k2_c1_tn_001",
      grade = "KHOI_2",
      level = "CAP_1_VONG_LOAI",
      type = "TN"
    )
    assertFalse(generator.matchesGradeAndRound(qWrongLevel, targetGrade = 2, targetRound = 2))
  }

  @Test
  fun testExamGenerationRatioAndFallback() {
    val generator = getGenerator()

    val testQuestions = JSONArray()
    for (i in 1..30) {
      testQuestions.put(JSONObject().apply {
        put("id", "k1_c1_tn_$i")
        put("type", "TN")
        put("grade", "KHOI_1")
        put("level", "CAP_1_VONG_LOAI")
        put("question", "Câu trắc nghiệm $i?")
        put("options", JSONArray(listOf("A", "B", "C", "D")))
        put("answer", "A")
      })
    }
    for (i in 1..15) {
      testQuestions.put(JSONObject().apply {
        put("id", "k1_c1_dt_$i")
        put("type", "DT")
        put("grade", "KHOI_1")
        put("level", "CAP_1_VONG_LOAI")
        put("question", "Điền từ $i [...]")
        put("answer", "từ $i")
      })
    }
    for (i in 1..5) {
      testQuestions.put(JSONObject().apply {
        put("id", "k1_c1_noi_$i")
        put("type", "NOI")
        put("grade", "KHOI_1")
        put("level", "CAP_1_VONG_LOAI")
        put("question", "Nối câu $i")
      })
    }
    for (i in 1..5) {
      testQuestions.put(JSONObject().apply {
        put("id", "k1_c1_chon_$i")
        put("type", "CHON")
        put("grade", "KHOI_1")
        put("level", "CAP_1_VONG_LOAI")
        put("question", "Chọn nhóm $i")
      })
    }
    for (i in 1..5) {
      testQuestions.put(JSONObject().apply {
        put("id", "k1_c1_sx_$i")
        put("type", "SX")
        put("grade", "KHOI_1")
        put("level", "CAP_1_VONG_LOAI")
        put("question", "Sắp xếp $i")
        put("elements", JSONArray(listOf("từ 1", "từ 2")))
      })
    }

    val examJsonStr = generator.generateDynamicMode2Exam(
      grade = 1,
      changOrRound = 1,
      seed = 42,
      localQuestionsJsonStr = testQuestions.toString(),
      targetCount = 30
    )

    val examJson = JSONObject(examJsonStr)
    assertEquals(1, examJson.getInt("grade"))
    assertEquals(1, examJson.getInt("round"))
    assertEquals(1, examJson.getInt("chang"))
    assertEquals(30, examJson.getInt("totalQuestions"))

    val dist = examJson.getJSONObject("typeDistribution")
    assertEquals(17, dist.getInt("TN"))
    assertEquals(7, dist.getInt("DT"))
    assertEquals(2, dist.getInt("NOI"))
    assertEquals(2, dist.getInt("CHON"))
    assertEquals(2, dist.getInt("SX"))
  }
}
