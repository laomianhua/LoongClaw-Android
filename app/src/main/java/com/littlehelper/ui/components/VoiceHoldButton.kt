package com.littlehelper.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.AppPhase
import com.littlehelper.R
import com.littlehelper.viewmodel.MainUiState

@Composable
fun VoiceHoldButton(
    uiState: MainUiState,
    onHoldStart: () -> Unit,
    onHoldEnd: (Long) -> Unit,
    onHoldCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phase = uiState.phase
    val isBusy = phase == AppPhase.PROCESSING || phase == AppPhase.SENDING
    val enabled = !isBusy
    val context = LocalContext.current

    val buttonText = when (phase) {
        AppPhase.PROCESSING -> stringResource(R.string.voice_button_processing)
        AppPhase.SENDING -> stringResource(R.string.voice_button_sending)
        AppPhase.RECORDING -> "松开 发送"
        else -> when {
            uiState.retryListening -> stringResource(R.string.voice_button_retry)
            uiState.choosingRecord -> stringResource(R.string.voice_button_choose)
            uiState.saveConfirmListening -> stringResource(R.string.voice_button_confirm)
            uiState.followUpListening -> stringResource(R.string.voice_button_follow_up)
            phase == AppPhase.ANSWERING -> stringResource(R.string.voice_button_interrupt)
            else -> "按住 说话"
        }
    }

    // 交互状态动画
    val isPressed = phase == AppPhase.RECORDING
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1.0f, label = "scale")
    val elevation by animateDpAsState(targetValue = if (isPressed) 2.dp else 12.dp, label = "elevation")

    // 红宝石对讲机拟物化色值
    val darkRuby = if (enabled) Color(0xFF6B0000) else Color(0xFF4A4A4A)
    val brightRuby = if (enabled) Color(0xFFB81414) else Color(0xFF8A8A8A)
    val metalBorder = Color(0xFF8A766A)

    Surface(
        modifier = modifier
            .size(width = 240.dp, height = 76.dp)
            .scale(scale)
            .shadow(elevation = elevation, shape = RoundedCornerShape(40.dp), clip = false)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    onHoldStart()

                    val up = waitForUpOrCancellation()
                    val upTime = System.currentTimeMillis()

                    if (up != null) {
                        val duration = upTime - downTime
                        if (duration < 500) {
                            onHoldCancel()
                            Toast.makeText(context, "录音时间太短", Toast.LENGTH_SHORT).show()
                        } else {
                            onHoldEnd(duration)
                        }
                    } else {
                        onHoldCancel()
                    }
                }
            },
        shape = RoundedCornerShape(40.dp),
        color = Color.Transparent,
        border = BorderStroke(2.5.dp, metalBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(brightRuby, darkRuby)))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // 文字
            Text(
                text = buttonText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )

            // 手绘对讲机网孔
            WalkieTalkieSpeakerDots(modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun WalkieTalkieSpeakerDots(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 16.dp, height = 32.dp)) {
        val dotRadius = 1.5.dp.toPx()
        val spacingX = 6.dp.toPx()
        val spacingY = 6.dp.toPx()
        val dotColor = Color(0x66000000) // 半透明黑色模拟凹陷的孔

        val startX = size.width / 2 - spacingX / 2
        val startY = size.height / 2 - spacingY * 2

        for (col in 0..1) {
            for (row in 0..4) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(
                        x = startX + col * spacingX,
                        y = startY + row * spacingY
                    )
                )
            }
        }
    }
}
