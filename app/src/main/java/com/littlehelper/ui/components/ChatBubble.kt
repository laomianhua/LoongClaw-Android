package com.littlehelper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.R
import com.littlehelper.upload.UploadMessageMarker
import com.littlehelper.ui.theme.AppColors
import kotlin.math.roundToInt

private val DeleteActionWidth = 72.dp

@Composable
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    onDeleteMessage: (String) -> Unit,
    swipeExpanded: Boolean = false,
    onSwipeExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isUser = message.role == ChatRole.USER
    val rowAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val swipeEnabled = !message.isPartial

    val displayText = when {
        message.isPartial && message.text.isBlank() -> "…"
        isUser -> UploadMessageMarker.displayTextForChat(message.text).ifBlank { "…" }
        else -> message.text
    }

    val bubbleColor = when {
        isUser -> AppColors.bubbleUser
        else -> AppColors.bubbleAssistant
    }

    val textColor = when {
        isUser -> AppColors.textOnUserBubble
        message.isError -> colorResource(R.color.error_text)
        else -> AppColors.textAssistant
    }

    val bubbleShape = RoundedCornerShape(dimensionResource(R.dimen.chat_bubble_corner_radius))
    val horizontalPadding = dimensionResource(R.dimen.chat_bubble_padding_horizontal)
    val verticalPadding = dimensionResource(R.dimen.chat_bubble_padding_vertical)
    val itemMargin = dimensionResource(R.dimen.chat_item_margin_vertical)
    val fontSizeValue = dimensionResource(R.dimen.chat_text_size).value
    val lineSpacingValue = dimensionResource(R.dimen.chat_line_spacing_extra).value
    val fontSize = fontSizeValue.sp
    val lineHeight = (fontSizeValue + lineSpacingValue).sp

    val density = LocalDensity.current
    val deleteWidthPx = with(density) { DeleteActionWidth.toPx() }

    var dragOffsetPx by remember(message.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(swipeExpanded) {
        if (!swipeExpanded && !isDragging) {
            dragOffsetPx = 0f
        }
    }

    val settledOffsetPx by animateFloatAsState(
        targetValue = if (swipeExpanded) -deleteWidthPx else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "chatBubbleSwipeSettle"
    )
    val displayOffsetPx = if (isDragging) dragOffsetPx else settledOffsetPx

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "提示") },
            text = { Text(text = "确定要删除这条对话吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(message.id)
                    showDeleteDialog = false
                    onSwipeExpandedChange(false)
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = itemMargin)
    ) {
        val bubbleMaxWidth = maxWidth * 0.96f

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(DeleteActionWidth)
                    .heightIn(min = 36.dp)
                    .background(Color(0xFFFF3B30), RoundedCornerShape(8.dp))
                    .clickable(enabled = swipeEnabled && swipeExpanded) {
                        showDeleteDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "删除",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(displayOffsetPx.roundToInt(), 0) }
                    .background(AppColors.systemBackground)
                    .then(
                        if (swipeEnabled) {
                            Modifier.pointerInput(message.id, swipeExpanded) {
                                detectHorizontalDragGestures(
                                    onDragStart = { isDragging = true },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragOffsetPx = (dragOffsetPx + dragAmount)
                                            .coerceIn(-deleteWidthPx, 0f)
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        val open = dragOffsetPx <= -deleteWidthPx / 2f
                                        dragOffsetPx = if (open) -deleteWidthPx else 0f
                                        onSwipeExpandedChange(open)
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffsetPx = if (swipeExpanded) -deleteWidthPx else 0f
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = rowAlignment
            ) {
                SelectionContainer {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .alpha(if (message.isPartial) 0.75f else 1f),
                        shape = bubbleShape,
                        color = bubbleColor,
                        border = if (!isUser) {
                            BorderStroke(0.5.dp, AppColors.bubbleAssistantBorder)
                        } else {
                            null
                        }
                    ) {
                        Text(
                            text = displayText,
                            modifier = Modifier.padding(
                                horizontal = horizontalPadding,
                                vertical = verticalPadding
                            ),
                            style = TextStyle(
                                color = textColor,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                letterSpacing = (-0.2).sp,
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}
