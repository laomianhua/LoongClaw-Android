package com.littlehelper.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.littlehelper.PanelState
import com.littlehelper.R
import com.littlehelper.ui.layout.ChatFlowSection
import com.littlehelper.ui.layout.ChatInputBar
import com.littlehelper.ui.layout.ChatInputBarLayout
import com.littlehelper.ui.layout.MultiFunctionPanel
import com.littlehelper.ui.layout.OpenClawStatusBanner
import com.littlehelper.ui.layout.PanelLayout
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.projection.ShellUiProjector
import com.littlehelper.ui.files.MyFilesSheet
import com.littlehelper.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onDeleteMessage: (String) -> Unit,
    showClearAllDialog: Boolean,
    onConfirmClearAll: () -> Unit,
    onDismissClearAll: () -> Unit,
    onPanelExpand: () -> Unit,
    onPanelCollapse: () -> Unit,
    onRetryOpenClawConnect: () -> Unit = {},
    onComposerDraftChange: (String) -> Unit = {},
    onSendComposerText: () -> Unit = {},
    onAttachmentPicked: (ByteArray, String, String) -> Unit = { _, _, _ -> },
    onClearPendingAttachment: () -> Unit = {},
    onToggleGatewayTts: () -> Unit = {},
    onNavigateModalHistory: (Int) -> Unit = {},
    onDeleteModalHistoryPage: () -> Unit = {},
    onOpenCanvasAmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showMyFiles by remember { mutableStateOf(false) }

    val displayMessages = ShellUiProjector.messages(uiState)
    val displayPanelState = ShellUiProjector.panelState(uiState)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .systemBarsPadding()
    ) {
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val density = LocalDensity.current
        val estimatedInputHeight = ChatInputBarLayout.sectionHeight
        var inputChromePx by remember { mutableIntStateOf(0) }
        val inputHeight = with(density) {
            if (inputChromePx > 0) inputChromePx.toDp() else estimatedInputHeight
        }
        val canvasHeight = (maxHeight - inputHeight).coerceAtLeast(0.dp)
        val expandedPanelHeight = canvasHeight * (2f / 3f)
        val targetPanelHeight = when (displayPanelState) {
            PanelState.COLLAPSED -> PanelLayout.openClawCollapsedHeight
            PanelState.EXPANDED -> expandedPanelHeight
        }
        val panelAnimationSpec = if (displayPanelState == PanelState.EXPANDED) {
            PanelLayout.modalHeightAnimationSpec
        } else {
            PanelLayout.heightAnimationSpec
        }
        val panelHeight by animateDpAsState(
            targetValue = targetPanelHeight,
            animationSpec = panelAnimationSpec,
            label = "panelHeight"
        )

        // 单列流式布局：聊天 weight(1f) 自动让出白板+输入栏，避免 Box 叠层 padding 对不齐
        Column(modifier = Modifier.fillMaxSize()) {
            OpenClawStatusBanner(
                uiState = uiState,
                onRetryConnect = onRetryOpenClawConnect
            )

            ChatFlowSection(
                messages = displayMessages,
                speakingMessageId = ShellUiProjector.speakingMessageId(uiState),
                panelState = displayPanelState,
                listState = listState,
                onDeleteMessage = onDeleteMessage,
                gatewayConnectionState = if (uiState.shellMode == ShellMode.OPENCLAW) {
                    ShellUiProjector.gatewayConnectionState(uiState)
                } else {
                    null
                },
                gatewayTtsEnabled = uiState.gatewayTtsEnabled,
                onToggleGatewayTts = if (uiState.shellMode == ShellMode.OPENCLAW) {
                    onToggleGatewayTts
                } else {
                    null
                },
                onRetryGatewayConnect = if (uiState.shellMode == ShellMode.OPENCLAW) {
                    onRetryOpenClawConnect
                } else {
                    null
                },
                onOpenMyFiles = if (uiState.shellMode == ShellMode.OPENCLAW) {
                    { showMyFiles = true }
                } else {
                    null
                },
                showAssistantThinking = ShellUiProjector.showAssistantThinking(uiState),
                inputScrollKey = imeBottom.toString(),
                panelLayoutKey = panelHeight.toString(),
                modifier = Modifier.weight(1f)
            )

            MultiFunctionPanel(
                panelHeight = panelHeight,
                panelState = displayPanelState,
                activeModule = uiState.shell.activeModule,
                moduleLoadState = ShellUiProjector.moduleLoadState(uiState),
                modulePayload = ShellUiProjector.modulePayload(uiState),
                modalState = ShellUiProjector.modalState(uiState),
                modalHistory = ShellUiProjector.modalHistory(uiState),
                onNavigateModalHistory = onNavigateModalHistory,
                onDeleteModalHistoryPage = onDeleteModalHistoryPage,
                onOpenCanvasAmap = onOpenCanvasAmap,
                onRequestExpand = onPanelExpand,
                onRequestCollapse = onPanelCollapse
            )

            ChatInputBar(
                uiState = uiState,
                connectionState = uiState.shell.connectionState,
                onDraftChange = onComposerDraftChange,
                onSendText = onSendComposerText,
                onAttachmentPicked = onAttachmentPicked,
                onClearPendingAttachment = onClearPendingAttachment,
                modifier = Modifier.onSizeChanged { inputChromePx = it.height }
            )
        }
    }

    if (showClearAllDialog) {
        ClearAllConfirmDialog(
            onConfirm = onConfirmClearAll,
            onDismiss = onDismissClearAll
        )
    }

    MyFilesSheet(
        visible = showMyFiles,
        onDismiss = { showMyFiles = false }
    )
}

@Composable
private fun ClearAllConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3E0E0))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.clear_all_dialog_title),
                color = Color(0xFFB71C1C),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.clear_all_dialog_message),
                modifier = Modifier.padding(top = 24.dp),
                color = Color(0xFF212121),
                fontSize = 24.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text(
                    text = stringResource(R.string.clear_all_confirm),
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.clear_all_cancel),
                    fontSize = 20.sp
                )
            }
        }
    }
}
