package com.littlehelper.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.R
import com.littlehelper.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromAlbum: () -> Unit,
    onPickFile: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.file_picker_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary
            )
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            FilePickerOption(
                label = stringResource(R.string.file_picker_take_photo),
                onClick = {
                    onTakePhoto()
                    onDismiss()
                }
            )
            FilePickerOption(
                label = stringResource(R.string.file_picker_from_album),
                onClick = {
                    onPickFromAlbum()
                    onDismiss()
                }
            )
            FilePickerOption(
                label = stringResource(R.string.file_picker_upload_file),
                onClick = {
                    onPickFile()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun FilePickerOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        fontSize = 16.sp,
        color = AppColors.textPrimary
    )
    HorizontalDivider()
}
