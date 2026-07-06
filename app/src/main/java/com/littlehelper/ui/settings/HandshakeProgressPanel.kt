package com.littlehelper.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.littlehelper.R
import com.littlehelper.settings.GatewayHandshakeProgress
import com.littlehelper.settings.HandshakeStep
import com.littlehelper.settings.HandshakeStepStatus
import com.littlehelper.settings.HandshakeStepUi
import com.littlehelper.shell.transport.ConnectFailureKind

@Composable
fun HandshakeProgressPanel(
    progress: GatewayHandshakeProgress,
    testingConnection: Boolean,
    failureKind: ConnectFailureKind?,
    modifier: Modifier = Modifier,
) {
    var deviceIdExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accentOrange = Color(0xFFE65100)
    val accentRed = Color(0xFFC62828)
    val accentGreen = Color(0xFF2E7D32)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.handshake_progress_title),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
        )

        progress.steps.forEach { stepUi ->
            val lineColor = when {
                stepUi.status == HandshakeStepStatus.FAILED -> accentRed
                stepUi.status == HandshakeStepStatus.WAITING_USER -> accentOrange
                stepUi.status == HandshakeStepStatus.DONE -> accentGreen
                stepUi.status == HandshakeStepStatus.RUNNING -> colors.primary
                else -> colors.onSurfaceVariant
            }
            Text(
                text = formatStepLine(stepUi, testingConnection),
                style = MaterialTheme.typography.bodySmall,
                color = lineColor,
                fontFamily = FontFamily.Monospace,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                modifier = Modifier.padding(top = 4.dp),
            )
            stepUi.hint?.takeIf { it.isNotBlank() }?.let { hint ->
                Text(
                    text = hint.prependIndent("                   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = lineColor,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (progress.pairingWaiting && !progress.deviceId.isNullOrBlank()) {
            Text(
                text = stringResource(
                    if (deviceIdExpanded) {
                        R.string.handshake_hide_device_id
                    } else {
                        R.string.handshake_show_device_id
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { deviceIdExpanded = !deviceIdExpanded },
            )
            if (deviceIdExpanded) {
                Text(
                    text = progress.deviceId.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (failureKind == ConnectFailureKind.PAIRING_REQUIRED) {
            Text(
                text = stringResource(R.string.settings_pairing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = accentOrange,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun formatStepLine(stepUi: HandshakeStepUi, testingConnection: Boolean): String {
    val label = when (stepUi.step) {
        HandshakeStep.TOKEN -> stringResource(R.string.handshake_step_token)
        HandshakeStep.PAIRING -> stringResource(R.string.handshake_step_pairing)
        HandshakeStep.APPROVED -> stringResource(R.string.handshake_step_approved)
        HandshakeStep.CONNECTED -> stringResource(R.string.handshake_step_connect)
    }
    val status = when (stepUi.status) {
        HandshakeStepStatus.DONE -> stringResource(R.string.handshake_status_done)
        HandshakeStepStatus.FAILED -> stringResource(R.string.handshake_status_failed)
        HandshakeStepStatus.WAITING_USER -> stringResource(R.string.handshake_status_waiting)
        HandshakeStepStatus.SKIPPED -> stringResource(R.string.handshake_status_skipped)
        HandshakeStepStatus.RUNNING -> {
            if (testingConnection) {
                stringResource(R.string.handshake_status_running)
            } else {
                stringResource(R.string.handshake_status_running_idle)
            }
        }
        HandshakeStepStatus.PENDING -> stringResource(R.string.handshake_status_pending)
    }
    return "$label → $status"
}
