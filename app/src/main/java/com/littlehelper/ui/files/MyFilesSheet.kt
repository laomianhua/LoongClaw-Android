package com.littlehelper.ui.files

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.R
import com.littlehelper.media.MyFilesRepository
import com.littlehelper.media.SystemFileOpener
import com.littlehelper.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFilesSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var files by remember { mutableStateOf<List<MyFilesRepository.LocalDownloadFile>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<MyFilesRepository.LocalDownloadFile?>(null) }

    fun refresh() {
        scope.launch {
            files = withContext(Dispatchers.IO) { MyFilesRepository.list(context) }
        }
    }

    LaunchedEffect(visible) {
        if (visible) refresh()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.my_files_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary
            )
            Text(
                text = stringResource(R.string.my_files_subtitle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = AppColors.textHint
            )

            if (files.isEmpty()) {
                Text(
                    text = stringResource(R.string.my_files_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp),
                    fontSize = 14.sp,
                    color = AppColors.textHint
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    items(files, key = { "${it.id}:${it.name}" }) { file ->
                        MyFileRow(
                            file = file,
                            onOpen = {
                                runCatching { SystemFileOpener.open(context, file) }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            R.string.my_files_open_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            },
                            onDelete = { pendingDelete = file }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.my_files_delete_title)) },
            text = { Text(stringResource(R.string.my_files_delete_message, file.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            MyFilesRepository.delete(context, file)
                        }
                        Toast.makeText(
                            context,
                            if (ok) R.string.my_files_delete_success else R.string.my_files_delete_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                        pendingDelete = null
                        refresh()
                    }
                }) {
                    Text(stringResource(R.string.my_files_delete_confirm), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.whiteboard_save_cancel))
                }
            }
        )
    }
}

@Composable
private fun MyFileRow(
    file: MyFilesRepository.LocalDownloadFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(
                if (file.mimeType.startsWith("image/")) {
                    R.drawable.ic_attachment_document
                } else if (file.mimeType == "application/pdf") {
                    R.drawable.ic_attachment_pdf
                } else {
                    R.drawable.ic_attachment_document
                }
            ),
            contentDescription = null,
            tint = AppColors.textPrimary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.textPrimary
            )
            Text(
                text = formatFileMeta(file),
                fontSize = 12.sp,
                color = AppColors.textHint
            )
        }
        IconButton(onClick = onDelete) {
            Text(text = "删除", fontSize = 13.sp, color = Color(0xFFFF3B30))
        }
    }
}

private fun formatFileMeta(file: MyFilesRepository.LocalDownloadFile): String {
    val sizeLabel = when {
        file.size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", file.size / (1024f * 1024f))
        file.size >= 1024 -> String.format(Locale.US, "%.0f KB", file.size / 1024f)
        else -> "${file.size} B"
    }
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(file.modifiedAt))
    return "$sizeLabel · $time"
}
