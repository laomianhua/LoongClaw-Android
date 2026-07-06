package com.littlehelper.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.littlehelper.BuildConfig
import com.littlehelper.R
import com.littlehelper.settings.AppUiLanguage
import com.littlehelper.settings.GatewayAuthMode
import com.littlehelper.settings.GatewayConnectionSettings
import com.littlehelper.settings.GatewayHandshakeProgress
import com.littlehelper.settings.GatewayHandshakeTestResult

private val SettingsFieldShape = RoundedCornerShape(12.dp)
private val SettingsCardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaySettingsSheet(
    modifier: Modifier = Modifier,
    form: GatewayConnectionSettings,
    gatewayTtsEnabled: Boolean,
    appUiLanguage: AppUiLanguage,
    testResult: GatewayHandshakeTestResult? = null,
    handshakeProgress: GatewayHandshakeProgress? = null,
    testingConnection: Boolean,
    onFormChange: (GatewayConnectionSettings) -> Unit,
    onGatewayTtsChange: (Boolean) -> Unit,
    onSelectAppUiLanguage: (AppUiLanguage) -> Unit,
    onTestConnection: () -> Unit,
    onSaveAndConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var authMenuExpanded by remember { mutableStateOf(false) }
    var tokenRevealed by remember { mutableStateOf(false) }
    var passwordRevealed by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val initialForm = remember { form }
    val isDirty = form != initialForm

    val colors = MaterialTheme.colorScheme
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = colors.surface,
    )

    fun attemptNavigateBack() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    BackHandler { attemptNavigateBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { attemptNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description),
                            tint = colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsCardShape,
                colors = cardColors,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_gateway_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_scan_qr_coming),
                            color = colors.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = stringResource(R.string.settings_protocol_value),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.settings_protocol_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SettingsFieldShape,
                        colors = settingsReadOnlyOutlinedTextFieldColors(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AuthModeDropdown(
                        authMode = form.authMode,
                        expanded = authMenuExpanded,
                        onExpandedChange = { authMenuExpanded = it },
                        onSelect = {
                            authMenuExpanded = false
                            tokenRevealed = false
                            passwordRevealed = false
                            onFormChange(form.copy(authMode = it))
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = form.host,
                            onValueChange = { onFormChange(form.copy(host = it)) },
                            label = { Text(stringResource(R.string.settings_host_label)) },
                            placeholder = { Text(stringResource(R.string.settings_host_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = SettingsFieldShape,
                            colors = settingsOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = form.port.takeIf { it in 1..65535 }?.toString().orEmpty(),
                            onValueChange = { text ->
                                val port = text.filter { ch -> ch.isDigit() }.toIntOrNull()
                                    ?: GatewayConnectionSettings.DEFAULT_PORT
                                onFormChange(form.copy(port = port))
                            },
                            label = { Text(stringResource(R.string.settings_port_label)) },
                            placeholder = {
                                Text(GatewayConnectionSettings.DEFAULT_PORT.toString())
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.45f),
                            shape = SettingsFieldShape,
                            colors = settingsOutlinedTextFieldColors(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (form.authMode == GatewayAuthMode.TOKEN) {
                        CredentialField(
                            value = form.plainToken,
                            onValueChange = { onFormChange(form.copy(plainToken = it)) },
                            label = stringResource(R.string.settings_token_label),
                            placeholder = stringResource(R.string.settings_credential_hint),
                            revealed = tokenRevealed,
                            onToggleReveal = { tokenRevealed = !tokenRevealed },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (form.authMode == GatewayAuthMode.PASSWORD) {
                        CredentialField(
                            value = form.plainPassword,
                            onValueChange = { onFormChange(form.copy(plainPassword = it)) },
                            label = stringResource(R.string.settings_password_label),
                            placeholder = stringResource(R.string.settings_password_hint),
                            revealed = passwordRevealed,
                            onToggleReveal = { passwordRevealed = !passwordRevealed },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = !testingConnection,
                            modifier = Modifier.weight(1f),
                            shape = SettingsFieldShape,
                        ) {
                            if (testingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.settings_test_connection))
                        }
                        Button(
                            onClick = onSaveAndConnect,
                            modifier = Modifier.weight(1f),
                            shape = SettingsFieldShape,
                        ) {
                            Text(stringResource(R.string.settings_save_and_connect))
                        }
                    }

                    if (testingConnection && handshakeProgress == null) {
                        Text(
                            text = stringResource(R.string.settings_test_handshake_connecting),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    handshakeProgress?.let { progress ->
                        HandshakeProgressPanel(
                            progress = progress,
                            testingConnection = testingConnection,
                            failureKind = testResult?.kind,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (handshakeProgress == null) {
                        testResult?.let { result ->
                            val statusColor = when {
                                result.success -> colors.onSurface
                                result.pairingRequired -> Color(0xFFE65100)
                                else -> colors.onSurfaceVariant
                            }
                            Text(
                                text = result.displayMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_save_connect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    if (handshakeProgress == null && testResult?.pairingRequired == true) {
                        Text(
                            text = stringResource(R.string.settings_pairing_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsCardShape,
                colors = cardColors,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppUiLanguage.entries.forEach { language ->
                            val label = when (language) {
                                AppUiLanguage.ZH -> stringResource(R.string.settings_language_zh)
                                AppUiLanguage.EN -> stringResource(R.string.settings_language_en)
                            }
                            FilterChip(
                                selected = language == appUiLanguage,
                                onClick = { onSelectAppUiLanguage(language) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsCardShape,
                colors = cardColors,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_sound_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_gateway_tts),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                        )
                        Switch(
                            checked = gatewayTtsEnabled,
                            onCheckedChange = onGatewayTtsChange,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsAboutCard(cardColors = cardColors)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onSaveAndConnect()
                    },
                ) {
                    Text(stringResource(R.string.settings_save_and_connect))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.settings_discard_confirm))
                }
            },
        )
    }
}

@Composable
private fun SettingsAboutCard(cardColors: CardColors) {
    val context = LocalContext.current
    val contactEmail = stringResource(R.string.about_contact_email)
    val contentColors = MaterialTheme.colorScheme

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_about_section),
                style = MaterialTheme.typography.titleMedium,
                color = contentColors.onSurface,
            )
            Text(
                text = stringResource(R.string.about_app_name),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColors.onSurface,
            )
            Text(
                text = stringResource(R.string.about_version_label) +
                    " " + stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColors.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_contact_label),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColors.onSurfaceVariant,
            )
            Text(
                text = contactEmail,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$contactEmail"))
                        context.startActivity(intent)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = contentColors.primary,
            )
        }
    }
}

@Composable
private fun settingsOutlinedTextFieldColors() = with(MaterialTheme.colorScheme) {
    TextFieldDefaults.colors(
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        focusedLabelColor = primary,
        unfocusedLabelColor = onSurfaceVariant,
        focusedPlaceholderColor = onSurfaceVariant,
        unfocusedPlaceholderColor = onSurfaceVariant,
        focusedContainerColor = surface,
        unfocusedContainerColor = surface,
        focusedIndicatorColor = primary,
        unfocusedIndicatorColor = outline,
        cursorColor = primary,
    )
}

@Composable
private fun settingsReadOnlyOutlinedTextFieldColors() = with(MaterialTheme.colorScheme) {
    TextFieldDefaults.colors(
        disabledTextColor = onSurface.copy(alpha = 0.6f),
        disabledLabelColor = onSurfaceVariant,
        disabledContainerColor = surfaceVariant.copy(alpha = 0.5f),
        disabledIndicatorColor = outline,
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        focusedLabelColor = primary,
        unfocusedLabelColor = onSurfaceVariant,
        focusedContainerColor = surface,
        unfocusedContainerColor = surface,
        focusedIndicatorColor = primary,
        unfocusedIndicatorColor = outline,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthModeDropdown(
    authMode: GatewayAuthMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (GatewayAuthMode) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = authModeLabel(authMode),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_auth_mode_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = SettingsFieldShape,
            colors = settingsOutlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            GatewayAuthMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = authModeLabel(mode),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun authModeLabel(mode: GatewayAuthMode): String = when (mode) {
    GatewayAuthMode.TOKEN -> stringResource(R.string.settings_auth_token)
    GatewayAuthMode.PASSWORD -> stringResource(R.string.settings_auth_password)
    GatewayAuthMode.NONE -> stringResource(R.string.settings_auth_none)
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    revealed: Boolean,
    onToggleReveal: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = onToggleReveal) {
                Text(
                    text = if (revealed) {
                        stringResource(R.string.settings_hide_credential)
                    } else {
                        stringResource(R.string.settings_show_credential)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsFieldShape,
        colors = settingsOutlinedTextFieldColors(),
    )
}
