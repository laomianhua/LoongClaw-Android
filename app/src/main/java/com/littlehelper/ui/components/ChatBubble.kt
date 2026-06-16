package com.littlehelper.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.R

private val SpeakingHighlightBackground = Color(0xFFFFF9C4)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    onDeleteMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isUser = message.role == ChatRole.USER
    val horizontalAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val maxWidth = if (isUser) 280.dp else 300.dp

    val displayText = if (message.isPartial && message.text.isBlank()) {
        "…"
    } else {
        message.text
    }

    val bubbleColor = when {
        isSpeaking && !isUser -> SpeakingHighlightBackground
        isUser -> Color.White.copy(alpha = 0.75f)
        else -> Color.White.copy(alpha = 0.75f)
    }

    val textColor = when {
        isUser -> colorResource(R.color.text_primary)
        message.isError -> colorResource(R.color.error_text)
        else -> colorResource(R.color.text_assistant)
    }

    val cornerRadius = dimensionResource(R.dimen.chat_bubble_corner_radius)
    val horizontalPadding = dimensionResource(R.dimen.chat_bubble_padding_horizontal)
    val verticalPadding = dimensionResource(R.dimen.chat_bubble_padding_vertical)
    val itemMargin = dimensionResource(R.dimen.chat_item_margin_vertical)
    val fontSizeValue = dimensionResource(R.dimen.chat_text_size).value
    val lineSpacingValue = dimensionResource(R.dimen.chat_line_spacing_extra).value
    val fontSize = fontSizeValue.sp
    val lineHeight = (fontSizeValue + lineSpacingValue).sp

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "提示") },
            text = { Text(text = "确定要删除这条对话吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(message.id)
                    showDeleteDialog = false
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = itemMargin),
        contentAlignment = horizontalAlignment
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .alpha(if (message.isPartial) 0.75f else 1f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (!message.isPartial) showDeleteDialog = true }
                ),
            shape = RoundedCornerShape(cornerRadius),
            color = bubbleColor,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Text(
                text = displayText,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                color = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = if (isSpeaking && !isUser) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
