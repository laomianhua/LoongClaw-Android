package com.littlehelper.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.littlehelper.R
import com.littlehelper.ui.theme.AppColors

@Composable
fun AssistantThinkingBubble(
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(dimensionResource(R.dimen.chat_bubble_corner_radius))
    val horizontalPadding = dimensionResource(R.dimen.chat_bubble_padding_horizontal)
    val verticalPadding = dimensionResource(R.dimen.chat_bubble_padding_vertical)
    val itemMargin = dimensionResource(R.dimen.chat_item_margin_vertical)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = itemMargin),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = bubbleShape,
            color = AppColors.bubbleAssistant,
            border = BorderStroke(1.dp, AppColors.bubbleAssistantBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    ThinkingDot(index = index)
                }
            }
        }
    }
}

@Composable
private fun ThinkingDot(index: Int) {
    val transition = rememberInfiniteTransition(label = "thinkingDot$index")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.25f at (index * 180)
                1f at (index * 180 + 300)
                0.25f at (index * 180 + 600)
                0.25f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingDotAlpha$index"
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(AppColors.textHint, CircleShape)
    )
}
