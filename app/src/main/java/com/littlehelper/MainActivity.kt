package com.littlehelper

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import com.littlehelper.reminder.NotificationHelper
import com.littlehelper.reminder.TodoDailyResetReceiver
import com.littlehelper.reminder.TodoDailyResetScheduler
import kotlinx.coroutines.launch
import com.littlehelper.reminder.ReminderReceiver
import com.littlehelper.speech.AudioRecorderManager
import com.littlehelper.tts.TtsManager
import com.littlehelper.ui.MainScreen
import com.littlehelper.ui.theme.LittleHelperTheme
import com.littlehelper.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var ttsManager: TtsManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果在下次操作时再次检查 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 强行打断闪屏：一旦开始准备绘制闪屏，立刻宣布应用已经完全就绪，直接移除它
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        audioRecorderManager = AudioRecorderManager(this)
        ttsManager = TtsManager(this)
        viewModel.attachAudioRecorderManager(audioRecorderManager)
        viewModel.attachTtsManager(ttsManager)

        requestNeededPermissions()
        handleReminderIntent(intent)

        lifecycleScope.launch {
            TodoDailyResetReceiver.runDailyTodoResetIfNewDay(applicationContext)
            TodoDailyResetScheduler(applicationContext).scheduleNextMidnight()
        }

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
                    records = viewModel.recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onHoldStart = viewModel::onHoldStart,
                    onHoldEnd = viewModel::onHoldEnd,
                    onHoldCancel = viewModel::onHoldCancel,
                    onToggleTodo = viewModel::toggleTodoDone,
                    onDeleteRecord = viewModel::deleteRecord,
                    onDeleteMessage = viewModel::deleteChatMessage,
                    onClearChatMessages = viewModel::clearChatMessages,
                    showClearAllDialog = showClearAllDialog,
                    onConfirmClearAll = {
                        showClearAllDialog = false
                        viewModel.confirmClearAllRecords()
                    },
                    onDismissClearAll = { showClearAllDialog = false },
                    onDrawerSelect = viewModel::selectDrawerCard,
                    onMapInstructionConsumed = viewModel::consumeMapInstruction
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ttsManager.bindActivity(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    override fun onDestroy() {
        audioRecorderManager.destroy()
        ttsManager.shutdown()
        super.onDestroy()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
        NotificationHelper.ensureChannel(this)
        checkExactAlarmPermission()
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "请在系统设置中允许「精确闹钟」权限，确保提醒准时触发",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    startActivity(
                        android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .apply { data = android.net.Uri.parse("package:$packageName") }
                    )
                } catch (_: Exception) {
                    // 部分厂商不支持直接跳转，忽略
                }
            }
        }
    }

    private fun handleReminderIntent(intent: android.content.Intent?) {
        val recordId = intent?.getLongExtra(ReminderReceiver.EXTRA_RECORD_ID, -1L) ?: -1L
        val message = intent?.getStringExtra(ReminderReceiver.EXTRA_MESSAGE)
        viewModel.onReminderOpened(recordId = recordId, message = message)
    }
}
