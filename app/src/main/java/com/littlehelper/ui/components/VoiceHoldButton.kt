package com.littlehelper.ui.components

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlehelper.AppPhase
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.projection.ShellUiProjector
import com.littlehelper.ui.theme.AppColors
import com.littlehelper.viewmodel.MainUiState
import kotlinx.coroutines.CancellationException

private val MicButtonSize = 64.dp
private val MicRippleContainerSize = 120.dp
private val WaveSlotWidth = 76.dp

@Composable
fun VoiceHoldButton(
    uiState: MainUiState,
    onHoldStart: () -> Unit,
    onHoldEnd: (Long) -> Unit,
    onHoldCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phase = ShellUiProjector.phase(uiState)
    val isRecording = phase == AppPhase.RECORDING
    // 录音中必须保持手势监听，否则 enabled=false 会取消 pointerInput，松手永远收不到 onHoldEnd。
    val blocksNewHold = when (uiState.shellMode) {
        ShellMode.OPENCLAW -> phase == AppPhase.SENDING
        else -> phase == AppPhase.PROCESSING || phase == AppPhase.SENDING
    }
    val micTint = if (blocksNewHold && !isRecording) AppColors.micDisabled else AppColors.micGreen
    val context = LocalContext.current
    val latestUiState by rememberUpdatedState(uiState)
    val latestOnHoldStart by rememberUpdatedState(onHoldStart)
    val latestOnHoldEnd by rememberUpdatedState(onHoldEnd)
    val latestOnHoldCancel by rememberUpdatedState(onHoldCancel)

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 0.92f else 1f,
        label = "micScale"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.width(WaveSlotWidth),
            contentAlignment = Alignment.CenterEnd
        ) {
            RecordingSoundWave(isActive = isRecording, mirrored = true)
        }

        Box(
            modifier = Modifier.size(MicRippleContainerSize),
            contentAlignment = Alignment.Center
        ) {
            if (!blocksNewHold || isRecording) {
                MicRippleRings(
                    buttonSize = MicButtonSize,
                    isRecording = isRecording
                )
            }

            Box(
                modifier = Modifier
                    .size(MicButtonSize)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(micTint)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val phaseAtDown = ShellUiProjector.phase(latestUiState)
                            val recordingAtDown = phaseAtDown == AppPhase.RECORDING
                            val blockAtDown = when (latestUiState.shellMode) {
                                ShellMode.OPENCLAW -> phaseAtDown == AppPhase.SENDING
                                else -> phaseAtDown == AppPhase.PROCESSING || phaseAtDown == AppPhase.SENDING
                            }
                            if (blockAtDown && !recordingAtDown) return@awaitEachGesture

                            val downTime = System.currentTimeMillis()
                            latestOnHoldStart()
                            try {
                                val up = waitForUpOrCancellation()
                                val upTime = System.currentTimeMillis()
                                if (up != null) {
                                    val duration = upTime - downTime
                                    if (duration < 500) {
                                        latestOnHoldCancel()
                                        Toast.makeText(context, "录音时间太短", Toast.LENGTH_SHORT).show()
                                    } else {
                                        latestOnHoldEnd(duration)
                                    }
                                } else {
                                    latestOnHoldCancel()
                                }
                            } catch (e: CancellationException) {
                                latestOnHoldCancel()
                                throw e
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                MicGlyph(
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier.width(WaveSlotWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            RecordingSoundWave(isActive = isRecording, mirrored = false)
        }
    }
}

@Composable
private fun MicGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyWidth = w * 0.42f
        val bodyHeight = h * 0.52f
        val bodyLeft = (w - bodyWidth) / 2f
        val bodyTop = h * 0.08f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(bodyWidth / 2f, bodyWidth / 2f)
        )
        val arcLeft = w * 0.18f
        val arcTop = h * 0.34f
        val arcSize = w * 0.64f
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(arcLeft, arcTop),
            size = Size(arcSize, arcSize * 0.72f),
            style = Stroke(width = w * 0.09f)
        )
        val stemWidth = w * 0.1f
        val stemTop = h * 0.66f
        val stemHeight = h * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset((w - stemWidth) / 2f, stemTop),
            size = Size(stemWidth, stemHeight),
            cornerRadius = CornerRadius(stemWidth / 2f, stemWidth / 2f)
        )
        val baseWidth = w * 0.34f
        val baseHeight = w * 0.09f
        drawRoundRect(
            color = tint,
            topLeft = Offset((w - baseWidth) / 2f, h * 0.84f),
            size = Size(baseWidth, baseHeight),
            cornerRadius = CornerRadius(baseHeight / 2f, baseHeight / 2f)
        )
    }
}

@Composable
private fun MicRippleRings(
    buttonSize: Dp,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val baseRadiusPx = with(density) { buttonSize.toPx() / 2f }
    val rippleColor = AppColors.micGreen
    val duration = if (isRecording) 1200 else 1800
    val maxExpansion = if (isRecording) 1f else 0.85f
    val peakAlpha = if (isRecording) 0.4f else 0.28f

    val infiniteTransition = rememberInfiniteTransition(label = "micRipples")

    val progress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    val progress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = duration,
                delayMillis = duration / 2,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    Canvas(modifier = modifier.size(MicRippleContainerSize)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        listOf(progress1, progress2).forEach { progress ->
            val radius = baseRadiusPx * (1f + progress * maxExpansion)
            val alpha = (1f - progress) * peakAlpha
            drawCircle(
                color = rippleColor.copy(alpha = alpha),
                radius = radius,
                center = center
            )
        }
    }
}
