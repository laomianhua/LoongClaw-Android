package com.littlehelper.shell.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.littlehelper.shell.modal.ModalBlock
import com.littlehelper.shell.modules.renderers.ChartLinePlaceholderRenderer
import com.littlehelper.shell.modules.renderers.MarkdownBlockRenderer
import com.littlehelper.shell.modules.renderers.TableBlockRenderer
import com.littlehelper.shell.modules.renderers.modalBlockUsesFillHeightWebView
import com.littlehelper.shell.modules.renderers.WebViewBlockRenderer
import com.littlehelper.shell.transport.GatewayCanvasAuth
import com.littlehelper.shell.transport.GatewayRuntime
import com.littlehelper.ui.theme.AppColors

/**
 * 多模态 Agent Canvas：垂直流式布局渲染 [ModalBlock] 列表（方案 §6.3）。
 */
@Composable
fun ModalCanvasHost(
    blocks: List<ModalBlock>,
    gatewayBaseUrl: String = GatewayRuntime.httpBaseUrl(),
    loadRevision: Long = 0L,
    onOpenAmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gatewayAuthToken = GatewayCanvasAuth.resolveCanvasHttpToken()
    if (blocks.isEmpty()) {
        ModalCanvasEmptyState(modifier = modifier)
        return
    }

    val useColumnLayout = blocks.size == 1 && modalBlockUsesFillHeightWebView(blocks.single())
    val contentPadding = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 12.dp,
        vertical = 10.dp
    )

    if (useColumnLayout) {
        key(loadRevision, blocks.single().id, blocks.single().type) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppColors.systemBackground)
                    .padding(contentPadding)
            ) {
                ModalBlockItem(
                    block = blocks.single(),
                    gatewayBaseUrl = gatewayBaseUrl,
                    gatewayAuthToken = gatewayAuthToken,
                    loadRevision = loadRevision,
                    onOpenAmap = onOpenAmap,
                    contentModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blocks, key = { "${it.id}:${it.type}:$loadRevision" }) { block ->
            ModalBlockItem(
                block = block,
                gatewayBaseUrl = gatewayBaseUrl,
                gatewayAuthToken = gatewayAuthToken,
                loadRevision = loadRevision,
                onOpenAmap = onOpenAmap
            )
        }
    }
}

@Composable
private fun ModalBlockItem(
    block: ModalBlock,
    gatewayBaseUrl: String,
    gatewayAuthToken: String,
    loadRevision: Long,
    onOpenAmap: () -> Unit = {},
    contentModifier: Modifier = Modifier.fillMaxWidth()
) {
    val amapAvailable by CanvasWebViewBridge.amapAvailable.collectAsState()
    val fillHeightWebView = modalBlockUsesFillHeightWebView(block)
    Column(
        modifier = if (fillHeightWebView) {
            contentModifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    ) {
        block.title?.let { title ->
            ModalBlockTitleRow(
                title = title,
                showAmapAction = amapAvailable && block.type.equals("webview", ignoreCase = true),
                onOpenAmap = onOpenAmap
            )
        }
        val blockModifier = if (fillHeightWebView) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
        } else {
            contentModifier
        }
        when (block.type.lowercase()) {
            "table" -> TableBlockRenderer(data = block.data, modifier = blockModifier)
            "markdown" -> MarkdownBlockRenderer(data = block.data, modifier = blockModifier)
            "chart/line" -> ChartLinePlaceholderRenderer(
                title = null,
                data = block.data,
                modifier = blockModifier
            )
            "webview" -> WebViewBlockRenderer(
                data = block.data,
                gatewayBaseUrl = gatewayBaseUrl,
                gatewayAuthToken = gatewayAuthToken,
                loadRevision = loadRevision,
                modifier = blockModifier
            )
            else -> UnsupportedBlockRenderer(block = block, modifier = blockModifier)
        }
    }
}

@Composable
private fun ModalBlockTitleRow(
    title: String,
    showAmapAction: Boolean,
    onOpenAmap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showAmapAction) {
            Text(
                text = "用高德地图查看",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF007AFF),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(onClick = onOpenAmap)
            )
        }
    }
}

@Composable
private fun UnsupportedBlockRenderer(
    block: ModalBlock,
    modifier: Modifier = Modifier
) {
    val raw = GsonBuilder().setPrettyPrinting().create().toJson(block.data)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "不支持的内容类型：${block.type}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE65100)
        )
        Text(
            text = raw,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 11.sp,
            color = AppColors.textHint,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ModalCanvasEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "白板就绪",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.textPrimary
        )
        Text(
            text = "等待 Agent 下发内容…",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            color = AppColors.textHint
        )
    }
}
