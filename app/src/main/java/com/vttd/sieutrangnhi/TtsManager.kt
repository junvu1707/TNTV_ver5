package com.vttd.sieutrangnhi

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.util.Locale

/**
 * Quản lý tính năng "Đọc đề bằng âm thanh" (Offline-First & On-Demand Audio Reader):
 * 1. Ưu tiên 1: Sử dụng Android Text-To-Speech (TTS Engine) đọc trực tiếp văn bản Tiếng Việt Locale("vi", "VN").
 * 2. Ưu tiên 2: Tải/Streaming tệp âm thanh MP3 trực tuyến nếu câu hỏi có chứa `audioUrl`.
 * 3. Tự động xử lý ngoại lệ: Hướng dẫn phụ huynh bật gói Tiếng Việt nếu thiết bị chưa cài sẵn TTS Tiếng Việt.
 * 4. Quản lý Lifecycle: Tự động dừng âm thanh khi chuyển câu hoặc thoát màn hình.
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

  companion object {
    private const val TAG = "TtsManager"
    private const val UTTERANCE_ID_QUESTION = "tntv_question_utterance"
  }

  private var tts: TextToSpeech? = null
  private var mediaPlayer: MediaPlayer? = null

  private var isTtsInitialized = false
  private var isVietnameseSupported = false

  var onSpeechStateChangedListener: ((isSpeaking: Boolean) -> Unit)? = null

  init {
    initTtsEngine()
  }

  /**
   * Khởi tạo Android TextToSpeech Engine
   */
  private fun initTtsEngine() {
    try {
      tts = TextToSpeech(context, this)
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi khởi tạo TextToSpeech Engine: ${e.message}")
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isTtsInitialized = true
      val locale = Locale.Builder().setLanguage("vi").setRegion("VN").build()
      val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED

      if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        Log.w(TAG, "Dữ liệu tiếng Việt (vi-VN) chưa được cài đặt trên thiết bị.")
        isVietnameseSupported = false
      } else {
        Log.d(TAG, "Khởi tạo Android TTS Tiếng Việt (vi-VN) thành công!")
        isVietnameseSupported = true
        setupTtsProgressListener()
      }
    } else {
      Log.e(TAG, "Khởi tạo TTS Engine thất bại. Mã lỗi: $status")
      isTtsInitialized = false
    }
  }

  private fun setupTtsProgressListener() {
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        notifyStateChanged(true)
      }

      override fun onDone(utteranceId: String?) {
        notifyStateChanged(false)
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        notifyStateChanged(false)
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        Log.e(TAG, "TTS Utterance Error code: $errorCode")
        notifyStateChanged(false)
      }
    })
  }

  /**
   * Đọc văn bản câu hỏi (hoặc phát MP3 từ audioUrl nếu có)
   */
  fun speakQuestion(
    text: String,
    audioUrl: String = "",
    activity: Activity? = null,
    onStateChanged: ((isSpeaking: Boolean) -> Unit)? = null
  ) {
    onStateChanged?.let { this.onSpeechStateChangedListener = it }
    stop()

    // 1. ƯU TIÊN 2: Nếu có audioUrl hợp lệ -> Streaming phát tệp âm thanh MP3
    if (audioUrl.isNotBlank() && (audioUrl.startsWith("http://") || audioUrl.startsWith("https://"))) {
      playAudioStream(audioUrl)
      return
    }

    // 2. ƯU TIÊN 1: Đọc trực tiếp bằng Android Text-To-Speech (vi-VN)
    if (text.isBlank()) {
      notifyStateChanged(false)
      return
    }

    // Chuẩn hóa văn bản Tiếng Việt ngắn gọn trước khi đọc
    val cleanText = text.replace(Regex("<[^>]*>"), "")
      .replace("[...]", "chấm chấm chấm")
      .replace("...", "chấm chấm chấm")
      .trim()

    if (isTtsInitialized && isVietnameseSupported && tts != null) {
      val params = Bundle().apply {
        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_QUESTION)
      }
      val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID_QUESTION)
      if (result != TextToSpeech.SUCCESS) {
        Log.e(TAG, "Lỗi phát giọng đọc TTS")
        notifyStateChanged(false)
      }
    } else {
      // Ngoại lệ: Máy chưa cài đặt giọng đọc Tiếng Việt -> Hiển thị hướng dẫn cho Phụ huynh
      notifyStateChanged(false)
      if (activity != null) {
        promptParentToEnableVietnameseTts(activity)
      } else {
        Toast.makeText(
          context,
          "Bé hãy nhờ ba mẹ bật giọng đọc Tiếng Việt trong Cài đặt máy nhé!",
          Toast.LENGTH_LONG
        ).show()
      }
    }
  }

  /**
   * Phát Streaming MP3 trực tuyến từ audioUrl
   */
  private fun playAudioStream(audioUrl: String) {
    try {
      mediaPlayer?.release()
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(audioUrl)
        setOnPreparedListener { mp ->
          notifyStateChanged(true)
          mp.start()
        }
        setOnCompletionListener {
          notifyStateChanged(false)
        }
        setOnErrorListener { _, what, extra ->
          Log.e(TAG, "Lỗi phát âm thanh MP3 streaming: $what, $extra")
          notifyStateChanged(false)
          true
        }
        prepareAsync()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi mở URL âm thanh: ${e.message}")
      notifyStateChanged(false)
    }
  }

  /**
   * Kiểm tra xem giọng đọc có đang phát hay không
   */
  fun isSpeaking(): Boolean {
    val isTtsSpeaking = tts?.isSpeaking == true
    val isMediaPlaying = mediaPlayer?.isPlaying == true
    return isTtsSpeaking || isMediaPlaying
  }

  /**
   * Dừng phát âm thanh ngay lập tức (Chuyển câu hoặc thoát màn hình)
   */
  fun stop() {
    try {
      if (tts?.isSpeaking == true) {
        tts?.stop()
      }
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.stop()
      }
      mediaPlayer?.release()
      mediaPlayer = null
      notifyStateChanged(false)
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi dừng âm thanh: ${e.message}")
    }
  }

  /**
   * Hướng dẫn phụ huynh bật giọng đọc Tiếng Việt trong Cài đặt máy
   */
  fun promptParentToEnableVietnameseTts(activity: Activity) {
    activity.runOnUiThread {
      AlertDialog.Builder(activity)
        .setTitle("🔊 Bật Giọng Đọc Tiếng Việt")
        .setMessage(
          "Để bé nghe đọc câu hỏi tự động, thiết bị cần mở gói ngôn ngữ Tiếng Việt.\n\n" +
          "Ba mẹ vui lòng thực hiện 3 bước đơn giản:\n" +
          "1. Bấm 'Vào Cài Đặt' bên dưới.\n" +
          "2. Chọn 'Chuyển văn bản thành giọng nói' (Text-to-speech).\n" +
          "3. Chọn Ngôn ngữ là 'Tiếng Việt' (Vietnamese).\n"
        )
        .setPositiveButton("Vào Cài Đặt ⚙️") { dialog, _ ->
          dialog.dismiss()
          openAndroidTtsSettings(activity)
        }
        .setNegativeButton("Đóng") { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    }
  }

  /**
   * Mở trực tiếp trang Cài đặt Text-to-speech của hệ điều hành Android
   */
  private fun openAndroidTtsSettings(activity: Activity) {
    try {
      val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      activity.startActivity(intent)
    } catch (_: Exception) {
      try {
        val fallbackIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(fallbackIntent)
      } catch (_: Exception) {
        Toast.makeText(activity, "Không thể mở trang cài đặt TTS tự động.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun notifyStateChanged(isSpeaking: Boolean) {
    onSpeechStateChangedListener?.invoke(isSpeaking)
  }

  /**
   * Giải phóng bộ nhớ khi Hủy Activity
   */
  fun destroy() {
    stop()
    try {
      tts?.shutdown()
      tts = null
    } catch (e: Exception) {
      Log.e(TAG, "Lỗi giải phóng TTS Engine: ${e.message}")
    }
  }
}
