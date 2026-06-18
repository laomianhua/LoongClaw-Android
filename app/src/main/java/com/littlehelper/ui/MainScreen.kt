package com.littlehelper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.littlehelper.viewmodel.MainUiState
import com.littlehelper.BuildConfig
import com.littlehelper.R
import com.littlehelper.data.MemoryRecord
import com.littlehelper.data.map.MapServiceFactory
import com.littlehelper.domain.map.IMapService
import com.littlehelper.presentation.mapview.MapCard
import com.littlehelper.presentation.stack.CardStackManager
import com.littlehelper.presentation.stack.DrawerCard
import com.littlehelper.presentation.stack.DrawerTabBar
import com.littlehelper.ui.components.ChatBubble
import com.littlehelper.ui.components.DashboardCard
import com.littlehelper.ui.components.VoiceHoldButton
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    records: List<MemoryRecord>,
    onHoldStart: () -> Unit,
    onHoldEnd: (Long) -> Unit,
    onHoldCancel: () -> Unit,
    onToggleTodo: (MemoryRecord) -> Unit,
    onDeleteRecord: (MemoryRecord) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearChatMessages: () -> Unit,
    showClearAllDialog: Boolean,
    onConfirmClearAll: () -> Unit,
    onDismissClearAll: () -> Unit,
    onDrawerSelect: (DrawerCard) -> Unit,
    onMapInstructionConsumed: (com.littlehelper.domain.map.MapExecuteResult?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearChatDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val dashboardState = rememberLazyListState()
    val lastMessageKey = uiState.messages.lastOrNull()?.let { "${it.id}:${it.text}:${it.isPartial}" }

    LaunchedEffect(uiState.messages.size, lastMessageKey) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val cardStackManager = remember { CardStackManager() }
    val mapService: IMapService = remember { MapServiceFactory.create() }

    DisposableEffect(mapService) {
        onDispose { mapService.onDestroy() }
    }

    LaunchedEffect(uiState.activeDrawerCard) {
        cardStackManager.switchToCard(uiState.activeDrawerCard)
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val peekHeight = 160.dp
    val sheetExpandedHeight = screenHeight * 0.72f

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    LaunchedEffect(uiState.drawerExpandRequest) {
        if (uiState.drawerExpandRequest > 0) {
            scaffoldState.bottomSheetState.expand()
        }
    }

    LaunchedEffect(uiState.mapExecutionToken) {
        if (uiState.mapExecutionToken == 0) return@LaunchedEffect
        val request = uiState.pendingMapInstruction ?: return@LaunchedEffect
        val supplementTts = uiState.pendingMapFallbackTts?.let {
            com.littlehelper.domain.map.MapTtsAuthorization.isSdkDynamicTtsAuthorized(it)
        } == true
        mapService.initialize(context, BuildConfig.AMAP_API_KEY)
        delay(400)
        val result = runCatching {
            mapService.executeInstruction(
                context,
                request.action,
                request.payload,
                supplementTts
            )
        }.getOrNull()
        onMapInstructionConsumed(result)
    }

    val glassShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val sheetScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetSwipeEnabled = false,
            containerColor = Color.Transparent,
            sheetContainerColor = Color.Transparent,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = null,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetExpandedHeight)
                        .background(
                            color = Color.White.copy(alpha = 0.95f),
                            shape = glassShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = glassShape
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(scaffoldState, sheetScope) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        sheetScope.launch {
                                            if (dragAmount < -12f) {
                                                scaffoldState.bottomSheetState.expand()
                                            } else if (dragAmount > 12f) {
                                                scaffoldState.bottomSheetState.partialExpand()
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 12.dp, bottom = 4.dp)
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            )

                            DrawerTabBar(
                                cards = cardStackManager.registeredCards,
                                activeCard = cardStackManager.activeCard,
                                onSelect = { card ->
                                    cardStackManager.selectCard(card)
                                    onDrawerSelect(card)
                                }
                            )
                        }

                        AnimatedContent(
                            targetState = cardStackManager.activeCard,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            label = "drawerContent"
                        ) { activeCard ->
                            when (activeCard) {
                                DrawerCard.NOTEBOOK -> NotebookDrawerContent(
                                    records = records,
                                    listState = dashboardState,
                                    onToggleTodo = onToggleTodo,
                                    onDeleteRecord = onDeleteRecord
                                )

                                DrawerCard.MAP -> MapCard(
                                    currentCard = activeCard,
                                    mapService = mapService,
                                    poiResults = uiState.mapPoiResults,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .systemBarsPadding()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(dimensionResource(R.dimen.chat_list_padding))
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

        VoiceHoldButton(
            uiState = uiState,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
            onHoldCancel = onHoldCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .size(48.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showClearChatDialog = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🧹", fontSize = 24.sp)
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
private fun NotebookDrawerContent(
    records: List<MemoryRecord>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleTodo: (MemoryRecord) -> Unit,
    onDeleteRecord: (MemoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
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
