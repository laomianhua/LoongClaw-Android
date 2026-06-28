package com.littlehelper.ui.layout

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.PendingAttachmentUi
import com.littlehelper.R
import com.littlehelper.attachment.AttachmentKind
import com.littlehelper.attachment.attachmentKindFor
import com.littlehelper.attachment.formatAttachmentSize
import com.littlehelper.ui.theme.AppColors

@Composable
fun AttachmentTray(
    attachment: PendingAttachmentUi,
    onClear: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val kind = attachmentKindFor(attachment.mimeType, attachment.fileName)
    val thumbnailBitmap = remember(attachment.thumbnailBytes) {
        attachment.thumbnailBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.headerActionBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                kind == AttachmentKind.IMAGE && thumbnailBitmap != null -> {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.panelSurface),
                        contentScale = ContentScale.Crop
                    )
                }
                kind == AttachmentKind.PDF -> {
                    AttachmentIconPlaceholder(
                        iconRes = R.drawable.ic_attachment_pdf,
                        contentDescription = attachment.fileName
                    )
                }
                else -> {
                    AttachmentIconPlaceholder(
                        iconRes = R.drawable.ic_attachment_document,
                        contentDescription = attachment.fileName,
                        tint = if (kind == AttachmentKind.IMAGE) AppColors.micGreen else Color.Unspecified
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = attachment.fileName,
                    fontSize = 14.sp,
                    color = AppColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatAttachmentSize(attachment.sizeBytes),
                    fontSize = 12.sp,
                    color = AppColors.textHint
                )
            }

            IconButton(
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = AppColors.textHint,
                    disabledContentColor = AppColors.textHint.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = "×",
                    fontSize = 20.sp,
                    color = if (enabled) AppColors.textHint else AppColors.textHint.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun AttachmentIconPlaceholder(
    iconRes: Int,
    contentDescription: String,
    tint: Color = Color.Unspecified
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(6.dp),
        color = AppColors.panelSurface
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(48.dp)
                .padding(10.dp),
            tint = tint
        )
    }
}
