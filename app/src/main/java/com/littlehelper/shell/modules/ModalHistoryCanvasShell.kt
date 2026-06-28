package com.littlehelper.shell.modules

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.littlehelper.R
import com.littlehelper.media.LittleHelperFileSaver
import com.littlehelper.media.StoredFileDownloader
import com.littlehelper.shell.modal.ModalBlock
import com.littlehelper.shell.modal.ModalHistoryState
import com.littlehelper.shell.transport.GatewayConfig
import com.littlehelper.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun ModalHistoryCanvasShell(
    blocks: List<ModalBlock>,
    loadRevision: Long,
    history: ModalHistoryState,
    onNavigateHistory: (Int) -> Unit,
    onDeleteHistoryPage: () -> Unit,
    onOpenAmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var pendingGalleryAction by remember { mutableStateOf<GallerySaveAction?>(null) }
    var pendingGalleryDownloadIndex by remember { mutableStateOf<Int?>(null) }
    var captureBounds by remember { mutableStateOf<Rect?>(null) }
    val storedFileDownloader = remember { StoredFileDownloader() }
    val canSwipe = history.size > 1
    val amapAvailable by CanvasWebViewBridge.amapAvailable.collectAsState()
    val webStoredAsset by CanvasWebViewBridge.storedImageAsset.collectAsState()
    val storedImageGallery by CanvasWebViewBridge.storedImageGallery.collectAsState()
    val blockStoredAsset = remember(blocks, loadRevision) {
        StoredImageAssetResolver.fromModalBlocks(
            blocks = blocks,
            gatewayBaseUrl = GatewayConfig.fromBuildConfig().httpBaseUrl()
        )
    }
    val isGalleryMode = storedImageGallery != null
    val downloadableAsset = if (isGalleryMode) null else webStoredAsset ?: blockStoredAsset
    val titledWebViewBlock = blocks.any {
        it.type.equals("webview", ignoreCase = true) && !it.title.isNullOrBlank()
    }
    val showStandaloneAmapAction = amapAvailable && !titledWebViewBlock
    val hasContent = blocks.isNotEmpty()

    fun saveScreenshot() {
        if (isSaving) return
        val bounds = captureBounds
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            Toast.makeText(context, R.string.whiteboard_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        scope.launch {
            val bitmap = WhiteboardScreenshotCapture.captureRegion(rootView, bounds)
            if (bitmap == null) {
                isSaving = false
                Toast.makeText(context, R.string.whiteboard_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = LittleHelperFileSaver.saveScreenshot(context, bitmap)
            bitmap.recycle()
            isSaving = false
            Toast.makeText(
                context,
                if (result.isSuccess) R.string.whiteboard_save_success else R.string.whiteboard_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun saveOriginalImage() {
        val asset = downloadableAsset ?: return
        if (isDownloading || isSaving) return
        isDownloading = true
        scope.launch {
            val result = storedFileDownloader.downloadToDownloads(context, asset)
            isDownloading = false
            Toast.makeText(
                context,
                if (result.isSuccess) R.string.whiteboard_download_success else R.string.whiteboard_download_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun performGalleryDownload(index: Int) {
        val asset = CanvasWebViewBridge.galleryItemAt(index) ?: return
        if (isDownloading || isSaving) return
        isDownloading = true
        scope.launch {
            val result = storedFileDownloader.downloadToDownloads(context, asset)
            isDownloading = false
            Toast.makeText(
                context,
                if (result.isSuccess) R.string.whiteboard_download_success else R.string.whiteboard_download_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingGalleryAction) {
                GallerySaveAction.SCREENSHOT -> saveScreenshot()
                GallerySaveAction.ORIGINAL -> saveOriginalImage()
                GallerySaveAction.GALLERY_ITEM -> {
                    val idx = pendingGalleryDownloadIndex
                    pendingGalleryDownloadIndex = null
                    if (idx != null) performGalleryDownload(idx)
                }
                null -> Unit
            }
        } else {
            Toast.makeText(context, R.string.whiteboard_save_permission_denied, Toast.LENGTH_SHORT).show()
        }
        pendingGalleryAction = null
        pendingGalleryDownloadIndex = null
    }

    fun runWithStoragePermission(action: GallerySaveAction, block: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                block()
            } else {
                pendingGalleryAction = action
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            block()
        }
    }

    fun requestGalleryDownload(index: Int) {
        if (CanvasWebViewBridge.galleryItemAt(index) == null) return
        pendingGalleryDownloadIndex = index
        runWithStoragePermission(GallerySaveAction.GALLERY_ITEM) {
            val idx = pendingGalleryDownloadIndex
            pendingGalleryDownloadIndex = null
            if (idx != null) performGalleryDownload(idx)
        }
    }

    LaunchedEffect(Unit) {
        CanvasWebViewBridge.galleryDownloadRequests.collect { index ->
            requestGalleryDownload(index)
        }
    }

    fun onConfirmSave() {
        showSaveDialog = false
        runWithStoragePermission(GallerySaveAction.SCREENSHOT, ::saveScreenshot)
    }

    fun onConfirmDownload() {
        showDownloadDialog = false
        runWithStoragePermission(GallerySaveAction.ORIGINAL, ::saveOriginalImage)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("提示") },
            text = { Text("从白板历史中移除这一页？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteHistoryPage()
                }) {
                    Text("移除", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.whiteboard_save_confirm_title)) },
            text = { Text(stringResource(R.string.whiteboard_save_confirm_message)) },
            confirmButton = {
                TextButton(onClick = ::onConfirmSave) {
                    Text(stringResource(R.string.whiteboard_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.whiteboard_save_cancel))
                }
            }
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.whiteboard_download_confirm_title)) },
            text = { Text(stringResource(R.string.whiteboard_download_confirm_message)) },
            confirmButton = {
                TextButton(onClick = ::onConfirmDownload) {
                    Text(stringResource(R.string.whiteboard_download_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(stringResource(R.string.whiteboard_save_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground)
            .modalHistorySwipeNavigation(
                enabled = canSwipe,
                onSwipeLeft = { onNavigateHistory(+1) },
                onSwipeRight = { onNavigateHistory(-1) }
            )
    ) {
        if (showStandaloneAmapAction) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "用高德地图查看",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF007AFF),
                    modifier = Modifier.clickable(onClick = onOpenAmap)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        captureBounds = Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    }
            ) {
                ModalCanvasHost(
                    blocks = blocks,
                    loadRevision = loadRevision,
                    onOpenAmap = onOpenAmap,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (hasContent || downloadableAsset != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloadableAsset != null) {
                        WhiteboardActionButton(
                            iconRes = R.drawable.ic_whiteboard_download,
                            contentDescription = stringResource(R.string.whiteboard_download_content_description),
                            onClick = { showDownloadDialog = true },
                            enabled = !isBusyForGallery(isSaving, isDownloading)
                        )
                    }
                    if (hasContent) {
                        WhiteboardActionButton(
                            iconRes = R.drawable.ic_whiteboard_save,
                            contentDescription = stringResource(R.string.whiteboard_save_content_description),
                            onClick = { showSaveDialog = true },
                            enabled = !isBusyForGallery(isSaving, isDownloading)
                        )
                    }
                }
            }
        }

        ModalHistoryIndicator(
            currentIndex = history.currentIndex,
            total = history.size,
            onLongPressDelete = { showDeleteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private enum class GallerySaveAction {
    SCREENSHOT,
    ORIGINAL,
    GALLERY_ITEM
}

private fun isBusyForGallery(isSaving: Boolean, isDownloading: Boolean): Boolean =
    isSaving || isDownloading

@Composable
private fun WhiteboardActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = Color(0xCC1C1C1E),
        shadowElevation = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ModalHistoryIndicator(
    currentIndex: Int,
    total: Int,
    onLongPressDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(total) { index ->
                val active = index == currentIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(5.dp)
                        .width(if (active) 14.dp else 5.dp)
                        .background(
                            color = if (active) AppColors.micGreen else AppColors.handle,
                            shape = RoundedCornerShape(2.5.dp)
                        )
                )
            }
            Text(
                text = "${currentIndex + 1}/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.textHint,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        ModalHistoryActionHint(
            onLongPressDelete = onLongPressDelete,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ModalHistoryActionHint(
    onLongPressDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hintStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 10.sp,
        color = AppColors.textHint
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "左右滑动切换", style = hintStyle)
        Text(text = " · ", style = hintStyle)
        Text(
            text = "长按删除",
            style = hintStyle,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressDelete() })
            }
        )
    }
}
