package com.littlehelper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.littlehelper.ui.theme.AppColors
import kotlin.math.sin

@Composable
fun RecordingSoundWave(
    isActive: Boolean,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    waveWidth: androidx.compose.ui.unit.Dp = 72.dp,
    waveHeight: androidx.compose.ui.unit.Dp = 36.dp,
    barColor: Color = AppColors.micGreen.copy(alpha = 0.55f)
) {
    val transition = rememberInfiniteTransition(label = "recordingWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "recordingWavePhase"
    )

    Canvas(
        modifier = modifier
            .width(waveWidth)
            .height(waveHeight)
            .alpha(if (isActive) 1f else 0f)
    ) {
        if (!isActive) return@Canvas

        val barWidth = size.width / (barCount * 1.65f)
        val gap = barWidth * 0.7f
        val maxBarHeight = size.height
        val startX = (size.width - (barCount * barWidth + (barCount - 1) * gap)) / 2f

        repeat(barCount) { index ->
            val visualIndex = if (mirrored) barCount - 1 - index else index
            val centerDistance = kotlin.math.abs(visualIndex - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
            val centerWeight = (1f - centerDistance * 0.65f).coerceIn(0.2f, 1f)
            val wave = sin((phase * 2f * Math.PI + index * 0.62).toFloat())
            val animated = ((wave + 1f) / 2f).coerceIn(0f, 1f)
            val barHeight = maxBarHeight * (0.22f + centerWeight * 0.48f + animated * 0.3f)
            val x = startX + index * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
