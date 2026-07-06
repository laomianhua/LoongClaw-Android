package com.littlehelper.shell.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.ShellNoteItem
import com.littlehelper.shell.modal.ModalSlotState
import com.littlehelper.ui.theme.AppColors

@Composable
fun ModuleHost(
    activeModule: ModuleId,
    moduleLoadState: ModuleLoadState,
    modulePayload: ModulePayload,
    modalState: com.littlehelper.shell.modal.ModalState,
    modalSlots: ModalSlotState = ModalSlotState(),
    onSelectModalTab: (String) -> Unit = {},
    onCloseModalTab: (String) -> Unit = {},
    onOpenCanvasAmap: () -> Unit = {},
    gatewayBaseUrl: String = "",
    modifier: Modifier = Modifier
) {
    if (modalState.isOpen) {
        ModalCanvasShell(
            blocks = modalState.blocks,
            loadRevision = modalState.loadRevision,
            modalSlots = modalSlots,
            onSelectTab = onSelectModalTab,
            onCloseTab = onCloseModalTab,
            onOpenAmap = onOpenCanvasAmap,
            gatewayBaseUrl = gatewayBaseUrl,
            modifier = modifier
        )
        return
    }

    when {
        moduleLoadState == ModuleLoadState.PRELOADING -> ModulePreloadPlaceholder(
            module = activeModule,
            modifier = modifier
        )

        modulePayload is ModulePayload.Note -> {
            ShellNoteList(items = modulePayload.items, modifier = modifier)
        }

        activeModule == ModuleId.WHITEBOARD && modulePayload is ModulePayload.Whiteboard -> {
            WhiteboardModuleRenderer(payload = modulePayload, modifier = modifier)
        }

        activeModule == ModuleId.WHITEBOARD -> {
            WhiteboardModuleRenderer(
                payload = ModulePayload.Whiteboard(),
                modifier = modifier
            )
        }

        activeModule == ModuleId.STOCK && modulePayload is ModulePayload.Stock -> {
            StockPlaceholder(payload = modulePayload, modifier = modifier)
        }

        else -> ModuleIdlePlaceholder(modifier = modifier)
    }
}

@Composable
private fun ModulePreloadPlaceholder(
    module: ModuleId,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 12.dp),
                color = AppColors.micGreen,
                strokeWidth = 2.dp
            )
            Text(
                text = when (module) {
                    ModuleId.WHITEBOARD -> "正在加载白板…"
                    ModuleId.STOCK -> "正在加载行情…"
                    else -> "正在加载…"
                },
                fontSize = 14.sp,
                color = AppColors.textHint
            )
        }
    }
}

@Composable
private fun ShellNoteList(
    items: List<ShellNoteItem>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${item.typeLabel}  ${item.timestamp}",
                    fontSize = 12.sp,
                    color = AppColors.textHint
                )
                Text(
                    text = item.content,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 15.sp,
                    color = AppColors.textPrimary,
                    fontWeight = if (item.done) FontWeight.Normal else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StockPlaceholder(
    payload: ModulePayload.Stock,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = payload.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = payload.symbol,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 14.sp,
                color = AppColors.textHint
            )
            payload.priceText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 22.sp,
                    color = AppColors.micGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModuleIdlePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "等待 OpenClaw 指令…", fontSize = 14.sp, color = AppColors.textHint)
    }
}
