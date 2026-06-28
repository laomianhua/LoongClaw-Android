package com.littlehelper.ui.layout

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.littlehelper.AppPhase
import com.littlehelper.R
import com.littlehelper.attachment.PickedAttachment
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.projection.ShellUiProjector
import com.littlehelper.ui.attachment.AttachmentFileReader
import com.littlehelper.ui.theme.AppColors
import com.littlehelper.viewmodel.MainUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 仿微信聊天输入栏。
 * 使用系统输入法（包括输入法自带的语音识别），无需 App 侧 ASR 权限。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    uiState: MainUiState,
    connectionState: ConnectionState,
    onDraftChange: (String) -> Unit,
    onSendText: () -> Unit,
    onAttachmentPicked: (ByteArray, String, String) -> Unit,
    onClearPendingAttachment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val draftText = uiState.inputComposer.draftText
    val pendingAttachment = uiState.inputComposer.pendingAttachment
    val phase = ShellUiProjector.phase(uiState)
    val isBusy = when (uiState.shellMode) {
        ShellMode.OPENCLAW -> phase == AppPhase.SENDING
        else -> phase == AppPhase.PROCESSING || phase == AppPhase.SENDING
    }
    val canSend = (draftText.isNotBlank() || pendingAttachment != null) && !isBusy &&
        (uiState.shellMode != ShellMode.OPENCLAW || connectionState == ConnectionState.ONLINE)
    val canAttach = !isBusy

    var showFilePickerSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun deliverAttachment(attachment: PickedAttachment?) {
        if (attachment == null) {
            Toast.makeText(context, R.string.file_picker_read_failed, Toast.LENGTH_SHORT).show()
            return
        }
        onAttachmentPicked(attachment.bytes, attachment.fileName, attachment.mimeType)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                deliverAttachment(
                    AttachmentFileReader.read(
                        context = context,
                        uri = uri,
                        fallbackFileName = cameraFallbackFileName()
                    )
                )
            }
        }
        pendingCameraUri = null
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { deliverAttachment(AttachmentFileReader.read(context, it)) }
    }

    val pickImageLegacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { deliverAttachment(AttachmentFileReader.read(context, it)) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { deliverAttachment(AttachmentFileReader.read(context, it)) }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture(context, takePictureLauncher) { pendingCameraUri = it }
        } else {
            Toast.makeText(context, R.string.file_picker_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    val requestMediaImagesPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchAlbumPicker(pickVisualMediaLauncher, pickImageLegacyLauncher)
        } else {
            Toast.makeText(context, R.string.file_picker_album_denied, Toast.LENGTH_SHORT).show()
        }
    }

    fun onTakePhotoClick() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                launchCameraCapture(context, takePictureLauncher) { pendingCameraUri = it }
            }
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onPickFromAlbumClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED -> {
                    launchAlbumPicker(pickVisualMediaLauncher, pickImageLegacyLauncher)
                }
                else -> requestMediaImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            launchAlbumPicker(pickVisualMediaLauncher, pickImageLegacyLauncher)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.intercomBackground)
            .padding(WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues())
    ) {
        pendingAttachment?.let { attachment ->
            AttachmentTray(
                attachment = attachment,
                onClear = onClearPendingAttachment,
                enabled = !isBusy
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { if (canAttach) showFilePickerSheet = true },
                enabled = canAttach,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = AppColors.textPrimary,
                    disabledContentColor = AppColors.textHint
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.file_picker_add_content_description),
                    tint = if (canAttach) AppColors.textPrimary else AppColors.textHint
                )
            }

            BasicTextField(
                value = draftText,
                onValueChange = onDraftChange,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = AppColors.textPrimary
                ),
                cursorBrush = SolidColor(AppColors.micGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSendText() }),
                maxLines = 4,
                decorationBox = { inner ->
                    if (draftText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.input_placeholder),
                            color = AppColors.textHint,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )

            IconButton(
                onClick = onSendText,
                enabled = canSend,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = AppColors.micGreen,
                    disabledContentColor = AppColors.textHint
                )
            ) {
                Text(
                    text = "↑",
                    fontSize = 26.sp,
                    color = if (canSend) AppColors.micGreen else AppColors.textHint
                )
            }
        }
    }

    if (showFilePickerSheet) {
        FilePickerSheet(
            onDismiss = { showFilePickerSheet = false },
            onTakePhoto = ::onTakePhotoClick,
            onPickFromAlbum = ::onPickFromAlbumClick,
            onPickFile = { openDocumentLauncher.launch(arrayOf("*/*")) }
        )
    }
}

private fun launchAlbumPicker(
    pickVisualMediaLauncher: androidx.activity.compose.ManagedActivityResultLauncher<
        PickVisualMediaRequest,
        Uri?
        >,
    pickImageLegacyLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pickVisualMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    } else {
        pickImageLegacyLauncher.launch("image/*")
    }
}

private fun launchCameraCapture(
    context: android.content.Context,
    takePictureLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>,
    onUriReady: (Uri) -> Unit
) {
    val photoFile = createCameraImageFile(context)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
    onUriReady(uri)
    takePictureLauncher.launch(uri)
}

private fun createCameraImageFile(context: android.content.Context): File {
    val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(cameraDir, cameraFallbackFileName())
}

private fun cameraFallbackFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "photo_$timestamp.jpg"
}

/** 估算高度供聊天列表计算留白（输入行约 60dp + 附件托盘约 76dp）。 */
object ChatInputBarLayout {
    val inputRowHeight = 60.dp
    val attachmentTrayHeight = 76.dp
    val sectionHeight = inputRowHeight + attachmentTrayHeight
}
