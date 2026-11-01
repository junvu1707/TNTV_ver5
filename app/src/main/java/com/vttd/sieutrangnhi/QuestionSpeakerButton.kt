package com.vttd.sieutrangnhi

import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nút bấm phát âm thanh câu hỏi ("Chiếc loa đọc đề") giao diện Jetpack Compose:
 * - Kích thước lớn, màu sắc bắt mắt tươi sáng dành riêng cho học sinh Tiểu học.
 * - Hiệu ứng sóng âm nhịp điệu (Audio Wave Pulse Animation) khi đang phát âm thanh.
 * - Tự động xử lý lifecycle: Dừng âm thanh khi chuyển câu hoặc thoát màn hình.
 */
@Composable
fun QuestionSpeakerButton(
  questionText: String,
  audioUrl: String = "",
  ttsManager: TtsManager,
  modifier: Modifier = Modifier,
) {
  var isSpeaking by remember { mutableStateOf(false) }

  // Tự động dừng âm thanh khi Composable bị dispose (Chuyển câu/thoát màn hình)
  DisposableEffect(questionText, audioUrl) {
    onDispose {
      ttsManager.stop()
      isSpeaking = false
    }
  }

  // Animation hiệu ứng sóng âm khi đang đọc
  val infiniteTransition = rememberInfiniteTransition(label = "speaker_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween<Float>(durationMillis = 500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "scale_pulse",
  )

  val buttonBgColor by animateColorAsState(
    targetValue = if (isSpeaking) Color(0xFF10B981) else Color(0xFFF59E0B),
    animationSpec = tween(durationMillis = 300),
    label = "bg_color",
  )

  Surface(
    modifier = modifier
      .scale(if (isSpeaking) pulseScale else 1.0f)
      .clip(RoundedCornerShape(16.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
      ) {
        if (isSpeaking) {
          ttsManager.stop()
          isSpeaking = false
        } else {
          ttsManager.speakQuestion(
            text = questionText,
            audioUrl = audioUrl,
          ) { speakingState ->
            isSpeaking = speakingState
          }
        }
      },
    shape = RoundedCornerShape(16.dp),
    color = buttonBgColor,
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .background(Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
          contentDescription = "Đọc câu hỏi",
          tint = Color.White,
          modifier = Modifier.size(22.dp),
        )
      }

      Text(
        text = if (isSpeaking) "Đang đọc..." else "Đọc đề",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
      )
    }
  }
}
