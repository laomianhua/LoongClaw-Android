package com.littlehelper.ui.layout



import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.littlehelper.R

import com.littlehelper.shell.model.ConnectionState

import com.littlehelper.shell.model.ShellMode

import com.littlehelper.shell.transport.ConnectFailureKind

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

            subtitle = null,

            action = null

        )



        shell.connectFailureKind == ConnectFailureKind.PAIRING_REQUIRED ||

            shell.pairingRequired -> BannerModel(

            background = Color(0xFFFFF3E0),

            title = shell.bannerError ?: "设备待配对",

            subtitle = failureSubtitle(shell, includeDeviceId = false),

            action = shell.connectUserAction ?: "批准后点此重试",

            deviceId = shell.deviceId,

        )



        shell.connectionState == ConnectionState.DEGRADED && !shell.bannerError.isNullOrBlank() -> BannerModel(

            background = Color(0xFFFFEBEE),

            title = shell.bannerError.orEmpty(),

            subtitle = failureSubtitle(shell, includeDeviceId = false),

            action = shell.connectUserAction ?: "点此重试"

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



private fun failureSubtitle(

    shell: com.littlehelper.shell.model.ShellUiState,

    includeDeviceId: Boolean,

): String? =

    buildString {

        shell.connectGatewayCode?.takeIf { it.isNotBlank() }?.let { code ->

            append("错误码：$code")

        }

        shell.bannerErrorDetail?.takeIf { it.isNotBlank() }?.let { detail ->

            if (isNotEmpty()) append('\n')

            append(detail)

        }

        if (includeDeviceId &&

            (shell.connectFailureKind == ConnectFailureKind.PAIRING_REQUIRED || shell.pairingRequired)

        ) {

            shell.deviceId?.takeIf { it.isNotBlank() }?.let { id ->

                if (isNotEmpty()) append('\n')

                append("设备 ID：$id")

            }

        }

    }.takeIf { it.isNotBlank() }



@Composable

private fun ConnectionBanner(

    banner: BannerModel,

    clickable: Boolean,

    onRetryConnect: () -> Unit,

    modifier: Modifier = Modifier

) {

    var deviceIdExpanded by remember(banner.deviceId) { mutableStateOf(false) }



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

                lineHeight = 15.sp

            )

        }

        if (!banner.deviceId.isNullOrBlank()) {

            Text(

                text = stringResource(

                    if (deviceIdExpanded) {

                        R.string.banner_hide_device_id

                    } else {

                        R.string.banner_show_device_id

                    }

                ),

                modifier = Modifier

                    .padding(top = 4.dp)

                    .clickable { deviceIdExpanded = !deviceIdExpanded },

                color = Color(0xFF007AFF),

                fontSize = 11.sp,

                fontWeight = FontWeight.Medium,

            )

            if (deviceIdExpanded) {

                Text(

                    text = banner.deviceId,

                    modifier = Modifier.padding(top = 4.dp),

                    color = Color(0xFF636366),

                    fontSize = 11.sp,

                    fontFamily = FontFamily.Monospace,

                    lineHeight = 15.sp,

                )

            }

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

    val deviceId: String? = null,

)


