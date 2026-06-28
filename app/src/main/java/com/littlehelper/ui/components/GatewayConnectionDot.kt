package com.littlehelper.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.ui.theme.AppColors

@Composable
fun GatewayConnectionDot(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, pulse) = when (connectionState) {
        ConnectionState.ONLINE -> AppColors.micGreen to false
        ConnectionState.CONNECTING -> Color(0xFFFF9500) to true
        ConnectionState.DEGRADED,
        ConnectionState.DISCONNECTED -> Color(0xFFFF3B30) to false
    }

    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "gatewayDotPulse")
        val animated by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gatewayDotAlpha"
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(13.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}
