package com.littlehelper

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littlehelper.tts.TtsManager
import com.littlehelper.ui.MainScreen
import com.littlehelper.ui.theme.LittleHelperTheme
import com.littlehelper.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var ttsManager: TtsManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果在下次操作时再次检查 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { it.remove() }

        ttsManager = TtsManager(this)
        viewModel.attachTtsManager(ttsManager)

        requestNeededPermissions()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var showClearAllDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                viewModel.clearAllConfirmation.collect {
                    showClearAllDialog = true
                }
            }

            LittleHelperTheme(darkTheme = false) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                MainScreen(
                    uiState = uiState,
                    onDeleteMessage = viewModel::deleteChatMessage,
                    showClearAllDialog = showClearAllDialog,
                    onConfirmClearAll = {
                        showClearAllDialog = false
                        viewModel.confirmClearAllRecords()
                    },
                    onDismissClearAll = { showClearAllDialog = false },
                    onPanelExpand = { viewModel.setPanelState(PanelState.EXPANDED) },
                    onPanelCollapse = { viewModel.setPanelState(PanelState.COLLAPSED) },
                    onRetryOpenClawConnect = viewModel::retryOpenClawConnect,
                    onComposerDraftChange = viewModel::updateComposerDraft,
                    onSendComposerText = viewModel::sendComposerText,
                    onAttachmentPicked = viewModel::onAttachmentPicked,
                    onClearPendingAttachment = viewModel::clearPendingAttachment,
                    onToggleGatewayTts = viewModel::toggleGatewayTts,
                    onNavigateModalHistory = viewModel::navigateModalHistory,
                    onDeleteModalHistoryPage = viewModel::deleteCurrentModalHistoryPage,
                    onOpenCanvasAmap = viewModel::openCanvasAmap
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ttsManager.bindActivity(this)
        viewModel.onAppResumed()
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
