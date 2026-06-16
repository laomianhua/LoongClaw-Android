package com.littlehelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.littlehelper.R
import com.littlehelper.ui.components.ChatBubble
import com.littlehelper.ui.components.VoiceHoldButton
import com.littlehelper.viewmodel.MainUiState

import com.littlehelper.ui.components.DashboardCard
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun MainScreen(
    uiState: MainUiState,
    records: List<com.littlehelper.data.MemoryRecord>,
    onHoldStart: () -> Unit,
    onHoldEnd: (Long) -> Unit,
    onHoldCancel: () -> Unit,
    onToggleTodo: (com.littlehelper.data.MemoryRecord) -> Unit,
    onDeleteRecord: (com.littlehelper.data.MemoryRecord) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearChatMessages: () -> Unit,
    showClearAllDialog: Boolean,
    onConfirmClearAll: () -> Unit,
    onDismissClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearChatDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val dashboardState = rememberLazyListState()
    val lastMessageKey = uiState.messages.lastOrNull()?.let { "${it.id}:${it.text}:${it.isPartial}" }

    LaunchedEffect(uiState.messages.size, lastMessageKey) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    // 红色按钮高度 76dp，底部 padding 32dp。
    // 收起时，让抽屉上沿停留在红色按钮上方一点点，露出大约 140dp 的高度（刚好能看到拉条和第一张卡片的边缘）
    val peekHeight = 140.dp 

    val scaffoldState = androidx.compose.material3.rememberBottomSheetScaffoldState(
        bottomSheetState = androidx.compose.material3.rememberStandardBottomSheetState(
            initialValue = androidx.compose.material3.SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent) // 强制 100% 透明
    ) {
        // 1. 最底层背景层
        /*
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.background),
            contentDescription = "Background",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        */

        androidx.compose.material3.BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            containerColor = Color.Transparent,
            sheetContainerColor = Color.Transparent,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = null, // 我们自己画，带在玻璃底盘内部
            sheetContent = {
                // Dashboard 玻璃底盘层 (60% height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.6f)
                        .background(
                            color = Color.White.copy(alpha = 0.95f), // 进一步增加不透明度，防止文字重叠干扰
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.8f), // 边缘高光
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 拟物化大底盘拉条抓手
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp, bottom = 8.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = dashboardState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 8.dp,
                                bottom = 120.dp // 留出底部按钮的空间
                            )
                        ) {
                            items(
                                items = records,
                                key = { it.id }
                            ) { record ->
                                DashboardCard(
                                    record = record,
                                    onToggleTodo = onToggleTodo,
                                    onDeleteRecord = onDeleteRecord
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // 2. 内容层 (Chat History)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .systemBarsPadding()
            ) {
                // Chat History (占满剩余空间)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        dimensionResource(R.dimen.chat_list_padding)
                    )
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        ChatBubble(
                            message = message,
                            isSpeaking = message.id == uiState.speakingMessageId,
                            onDeleteMessage = onDeleteMessage
                        )
                    }
                }
            }
        }

        // 顶层：红色对讲机大按钮，悬浮在最上方
        VoiceHoldButton(
            uiState = uiState,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
            onHoldCancel = onHoldCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // 顶层：右上角极简扫把图标
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp) // 避开状态栏
                .size(48.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showClearChatDialog = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🧹",
                fontSize = 24.sp
            )
        }
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text(text = "提示") },
            text = { Text(text = "是否删除当前所有对话？") },
            confirmButton = {
                TextButton(onClick = {
                    onClearChatMessages()
                    showClearChatDialog = false
                }) {
                    Text("是", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("否")
                }
            }
        )
    }

    if (showClearAllDialog) {
        ClearAllConfirmDialog(
            onConfirm = onConfirmClearAll,
            onDismiss = onDismissClearAll
        )
    }
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
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
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
