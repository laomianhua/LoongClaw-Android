package com.littlehelper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.shell.modal.ModalSlot
import com.littlehelper.shell.modal.ModalSlotState
import com.littlehelper.ui.theme.AppColors

@Composable
fun ModalTabBar(
    slots: ModalSlotState,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slots.mruList.isEmpty()) return

    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.systemBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.visibleTabIds.forEach { id ->
            val slot = slots.slotMap[id] ?: return@forEach
            ModalTabChip(
                slot = slot,
                selected = id == slots.activeId,
                onClick = { onSelectTab(id) },
                onLongPress = { onCloseTab(id) }
            )
        }
        if (slots.overflowTabIds.isNotEmpty()) {
            Box {
                Text(
                    text = "…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.textHint,
                    modifier = Modifier
                        .clickable { overflowExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    slots.overflowTabIds.forEach { id ->
                        val slot = slots.slotMap[id] ?: return@forEach
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = tabLabel(slot),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onSelectTab(id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalTabChip(
    slot: ModalSlot,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) AppColors.micGreen.copy(alpha = 0.18f) else AppColors.handle.copy(alpha = 0.35f)
    val textColor = if (selected) AppColors.micGreen else AppColors.textPrimary

    Text(
        text = tabLabel(slot),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(background, RoundedCornerShape(14.dp))
            .pointerInput(slot.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

private fun tabLabel(slot: ModalSlot): String {
    val raw = slot.title?.trim().orEmpty().ifBlank { slot.id }
    return if (raw.length <= 14) raw else raw.take(12) + "…"
}
