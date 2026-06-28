package com.littlehelper.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.viewmodel.MainUiState

@Composable
fun OpenClawStatusBanner(
    uiState: MainUiState,
    onRetryConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.shellMode != ShellMode.OPENCLAW) return

    val shell = uiState.shell

    if (shell.connectionState == ConnectionState.ONLINE &&
        !shell.modalParseWarning.isNullOrBlank()
    ) {
        ConnectionBanner(
            banner = BannerModel(
                background = Color(0xFFFFF8E1),
                title = shell.modalParseWarning.orEmpty(),
                subtitle = null,
                action = null
            ),
            clickable = false,
            onRetryConnect = onRetryConnect,
            modifier = modifier
        )
        return
    }

    if (!shell.connectionBannerVisible) return

    val banner = when {
        shell.connectionState == ConnectionState.CONNECTING -> BannerModel(
            background = Color(0xFFE8F0FE),
            title = "正在连接 OpenClaw Gateway…",
            subtitle = shell.deviceId?.let { "设备 ID：$it" },
            action = null
        )

        shell.pairingRequired -> BannerModel(
            background = Color(0xFFFFF3E0),
            title = "设备待配对",
            subtitle = buildString {
                append("请在 Gateway Control UI 批准此设备")
                shell.deviceId?.let { append("\n$it") }
            },
            action = "批准后点此重试",
            monospaceSubtitle = true
        )

        shell.connectionState == ConnectionState.DEGRADED && !shell.bannerError.isNullOrBlank() -> BannerModel(
            background = Color(0xFFFFEBEE),
            title = shell.bannerError.orEmpty(),
            subtitle = shell.deviceId?.let { "设备 ID：$it" },
            action = "点此重试"
        )

        shell.connectionState == ConnectionState.ONLINE &&
            !shell.modalParseWarning.isNullOrBlank() -> return

        else -> return
    }

    ConnectionBanner(
        banner = banner,
        clickable = banner.action != null,
        onRetryConnect = onRetryConnect,
        modifier = modifier
    )
}

@Composable
private fun ConnectionBanner(
    banner: BannerModel,
    clickable: Boolean,
    onRetryConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(banner.background)
            .then(
                if (clickable) {
                    Modifier.clickable(onClick = onRetryConnect)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = banner.title,
            color = Color(0xFF1C1C1E),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        banner.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFF636366),
                fontSize = 11.sp,
                fontFamily = if (banner.monospaceSubtitle) FontFamily.Monospace else FontFamily.Default,
                lineHeight = 15.sp
            )
        }
        banner.action?.let { action ->
            Text(
                text = action,
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFF007AFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class BannerModel(
    val background: Color,
    val title: String,
    val subtitle: String?,
    val action: String?,
    val monospaceSubtitle: Boolean = false
)
