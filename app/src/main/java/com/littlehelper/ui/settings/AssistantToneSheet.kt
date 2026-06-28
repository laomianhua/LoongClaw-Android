package com.littlehelper.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.settings.AssistantTone
import com.littlehelper.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantToneSheet(
    currentTone: AssistantTone,
    onSelectTone: (AssistantTone) -> Unit,
    onDismiss: () -> Unit
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
                text = "助手语气",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary
            )
            Text(
                text = "无 patch 权限时，语气会与你的话合在一条消息里发送，避免助手单独确认。",
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                fontSize = 13.sp,
                color = AppColors.textHint,
                lineHeight = 18.sp
            )
            HorizontalDivider()
            AssistantTone.entries.forEach { tone ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTone(tone) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = tone == currentTone,
                        onClick = { onSelectTone(tone) }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = tone.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = tone.subtitle,
                            fontSize = 12.sp,
                            color = AppColors.textHint
                        )
                    }
                }
            }
        }
    }
}
