package com.littlehelper.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.PanelState
import com.littlehelper.R
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.ui.components.AssistantThinkingBubble
import com.littlehelper.ui.components.ChatBubble
import com.littlehelper.ui.components.GatewayConnectionDot
import com.littlehelper.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatFlowSection(
    messages: List<ChatMessage>,
    speakingMessageId: String?,
    panelState: PanelState,
    listState: LazyListState,
    onDeleteMessage: (String) -> Unit,
    gatewayConnectionState: ConnectionState? = null,
    gatewayTtsEnabled: Boolean = true,
    onToggleGatewayTts: (() -> Unit)? = null,
    onRetryGatewayConnect: (() -> Unit)? = null,
    onOpenMyFiles: (() -> Unit)? = null,
    showAssistantThinking: Boolean = false,
    inputScrollKey: String? = null,
    panelLayoutKey: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var swipeExpandedMessageId by remember { mutableStateOf<String?>(null) }
    val displayMessages = remember(messages) { messages.asReversed() }
    val lastScrollKey = when {
        showAssistantThinking -> "thinking"
        else -> messages.lastOrNull()?.let { "${it.id}:${it.text.length}:${it.isPartial}" }
    }

    // reverseLayout：index 0 在视觉底部（最新消息）
    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            firstVisible <= 1
        }
    }

    val shouldForceScroll by remember {
        derivedStateOf {
            if (showAssistantThinking) return@derivedStateOf true
            val last = messages.lastOrNull() ?: return@derivedStateOf false
            last.role == ChatRole.USER
        }
    }

    LaunchedEffect(messages.size, lastScrollKey, showAssistantThinking) {
        if (messages.isEmpty() && !showAssistantThinking) return@LaunchedEffect
        if (shouldForceScroll || isNearBottom) {
            listState.scrollToLatest()
        }
    }

    LaunchedEffect(panelState) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (panelState == PanelState.EXPANDED) {
            listState.scrollToLatest()
            delay(PanelLayout.modalAnimationDurationMillis.toLong() + 48L)
            listState.scrollToLatest()
        } else if (shouldForceScroll || isNearBottom) {
            listState.scrollToLatest()
        }
    }

    LaunchedEffect(panelLayoutKey) {
        if (panelLayoutKey == null || messages.isEmpty()) return@LaunchedEffect
        if (panelState != PanelState.EXPANDED) return@LaunchedEffect
        listState.scrollToLatest()
    }

    LaunchedEffect(inputScrollKey) {
        if (inputScrollKey == null || messages.isEmpty()) return@LaunchedEffect
        if (shouldForceScroll || isNearBottom) {
            listState.scrollToLatest()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground)
    ) {
        ChatSectionHeader(
            gatewayConnectionState = gatewayConnectionState,
            gatewayTtsEnabled = gatewayTtsEnabled,
            onToggleGatewayTts = onToggleGatewayTts,
            onRetryGatewayConnect = onRetryGatewayConnect,
            onOpenMyFiles = onOpenMyFiles
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    if (panelState == PanelState.EXPANDED && messages.isNotEmpty()) {
                        scope.launch { listState.scrollToLatest() }
                    }
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    start = dimensionResource(R.dimen.chat_list_padding),
                    end = dimensionResource(R.dimen.chat_list_padding),
                    top = 8.dp,
                    bottom = 8.dp
                )
            ) {
                if (showAssistantThinking) {
                    item(key = "assistant-thinking") {
                        AssistantThinkingBubble()
                    }
                }
                items(
                    items = displayMessages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(
                        message = message,
                        isSpeaking = message.id == speakingMessageId,
                        onDeleteMessage = onDeleteMessage,
                        swipeExpanded = swipeExpandedMessageId == message.id,
                        onSwipeExpandedChange = { expanded ->
                            swipeExpandedMessageId = if (expanded) message.id else null
                        }
                    )
                }
            }

            if (!isNearBottom && messages.isNotEmpty()) {
                TextButton(
                    onClick = { scope.launch { listState.scrollToLatest() } },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .background(AppColors.panelSurface, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "↓ 回到底部",
                        fontSize = 14.sp,
                        color = AppColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSectionHeader(
    gatewayConnectionState: ConnectionState? = null,
    gatewayTtsEnabled: Boolean = true,
    onToggleGatewayTts: (() -> Unit)? = null,
    onRetryGatewayConnect: (() -> Unit)? = null,
    onOpenMyFiles: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val canRetryConnect = gatewayConnectionState != null &&
        gatewayConnectionState != ConnectionState.ONLINE &&
        onRetryGatewayConnect != null
    val retryConnectLabel = stringResource(R.string.gateway_connect_retry)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (canRetryConnect) {
                        Modifier
                            .clickable(onClick = onRetryGatewayConnect!!)
                            .semantics { contentDescription = retryConnectLabel }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (gatewayConnectionState != null) {
                GatewayConnectionDot(connectionState = gatewayConnectionState)
            }
        }
        Text(
            text = "与龙虾助手的对话",
            modifier = Modifier.weight(1f),
            fontSize = dimensionResource(R.dimen.chat_text_size).value.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onOpenMyFiles != null) {
                    IconButton(
                        onClick = onOpenMyFiles,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = stringResource(R.string.my_files_content_description),
                            tint = AppColors.textPrimary
                        )
                    }
                }
                if (onToggleGatewayTts != null) {
                    IconButton(
                        onClick = onToggleGatewayTts,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (gatewayTtsEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off
                            ),
                            contentDescription = stringResource(
                                if (gatewayTtsEnabled) R.string.gateway_tts_on else R.string.gateway_tts_off
                            ),
                            tint = AppColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/** reverseLayout 下 index 0 为视觉底部（最新）。 */
private suspend fun LazyListState.scrollToLatest() {
    if (layoutInfo.totalItemsCount <= 0) return
    scrollToItem(0)
}
