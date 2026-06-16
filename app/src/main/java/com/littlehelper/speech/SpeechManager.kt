package com.littlehelper.speech

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.littlehelper.BuildConfig
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * 按住说话：松开按钮结束录音。
 *
 * 长句可能触发云端 [SpeechRecognizer.ERROR_NETWORK_TIMEOUT]；
 * 按住期间会自动续听并把多段 partial 拼成一句，不设置 MINIMUM_LENGTH（该值表示最短录音时长，误设会导致无法结束）。
 */
class SpeechManager(activity: Activity) {
    private val activityRef = WeakReference(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var userRequestedStop = false
    private var isRestarting = false
    private var continuationCount = 0
    private var lastPartialText = ""
    private var accumulatedText = ""
    private var resultDelivered = false
    private var lastIntent: Intent? = null
    private var clientStopFallbackRunnable: Runnable? = null
    private var restartRunnable: Runnable? = null
    private var shortAnswerMode = false

    companion object {
        /** Logcat 过滤标签：adb logcat -s LittleHelperSTT */
        private const val TAG = "LittleHelperSTT"

        private const val COMPLETE_SILENCE_MS = 10_000L
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 4_000L
        private const val SHORT_COMPLETE_SILENCE_MS = 3_500L
        private const val SHORT_POSSIBLY_COMPLETE_SILENCE_MS = 1_800L
        private const val CLIENT_STOP_FALLBACK_MS = 4_000L
        private const val SHORT_CLIENT_STOP_FALLBACK_MS = 2_500L
        private const val RESTART_DELAY_MS = 250L
        /** 一次按住最多续听次数（应对长句云端超时）。 */
        private const val MAX_CONTINUATIONS = 4
        private const val MAX_SHORT_ANSWER_CONTINUATIONS = 6
    }

    var onPartialText: ((String) -> Unit)? = null
    var onFinalText: ((String) -> Unit)? = null
    var onPartialRecovery: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningChanged: ((Boolean) -> Unit)? = null

    fun isAvailable(): Boolean {
        val activity = activityRef.get() ?: return false
        return SpeechRecognizer.isRecognitionAvailable(activity)
    }

    fun startListening(shortAnswerMode: Boolean = false) {
        val activity = activityRef.get() ?: return
        if (listening) return

        this.shortAnswerMode = shortAnswerMode

        if (!isAvailable()) {
            onError?.invoke("当前手机不支持语音识别")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = createSpeechRecognizer(activity)?.apply {
                setRecognitionListener(recognitionListener)
            }
        }

        resetSession()
        lastIntent = buildRecognizerIntent()
        listening = true
        onListeningChanged?.invoke(true)
        Log.i(TAG, "startListening, language=${Locale.CHINA.toLanguageTag()}, shortAnswer=$shortAnswerMode")
        speechRecognizer?.startListening(lastIntent)
    }

    private fun createSpeechRecognizer(activity: Activity): SpeechRecognizer? {
        logRecognitionEngineInfo(activity)
        val component = resolveDefaultRecognitionService(activity)
        return if (component != null) {
            Log.i(TAG, "bind RecognitionService: ${component.packageName} / ${component.className}")
            SpeechRecognizer.createSpeechRecognizer(activity, component)
        } else {
            Log.w(TAG, "no default RecognitionService, use system fallback")
            SpeechRecognizer.createSpeechRecognizer(activity)
        }
    }

    private fun logRecognitionEngineInfo(activity: Activity) {
        val pm = activity.packageManager
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val allServices = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
        Log.i(TAG, "device has ${allServices.size} RecognitionService(s):")
        allServices.forEach { resolveInfo ->
            val service = resolveInfo.serviceInfo
            Log.i(TAG, "  - ${service.packageName} / ${service.name}")
        }
        val defaultServices = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (defaultServices.isEmpty()) {
            Log.w(TAG, "DEFAULT RecognitionService: (none)")
        } else {
            defaultServices.forEach { resolveInfo ->
                val service = resolveInfo.serviceInfo
                Log.i(TAG, "DEFAULT RecognitionService: ${service.packageName} / ${service.name}")
            }
        }
    }

    private fun resolveDefaultRecognitionService(activity: Activity): ComponentName? {
        val services = activity.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (services.isEmpty()) return null
        val service = services.first().serviceInfo
        return ComponentName(service.packageName, service.name)
    }

    fun stopListening() {
        if (!listening) return
        userRequestedStop = true
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        cancelClientStopFallback()
        cancelRestart()
        listening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun buildRecognizerIntent(): Intent {
        val completeSilence = if (shortAnswerMode) SHORT_COMPLETE_SILENCE_MS else COMPLETE_SILENCE_MS
        val possiblyCompleteSilence =
            if (shortAnswerMode) SHORT_POSSIBLY_COMPLETE_SILENCE_MS else POSSIBLY_COMPLETE_SILENCE_MS
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                completeSilence
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                possiblyCompleteSilence
            )
        }
    }

    private fun resetSession() {
        cancelClientStopFallback()
        cancelRestart()
        lastPartialText = ""
        accumulatedText = ""
        continuationCount = 0
        resultDelivered = false
        userRequestedStop = false
        isRestarting = false
    }

    private fun cancelClientStopFallback() {
        clientStopFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        clientStopFallbackRunnable = null
    }

    private fun cancelRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
        isRestarting = false
    }

    private fun extractText(results: Bundle?): String {
        return results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun currentDisplayText(): String {
        return mergeSpeechSegments(accumulatedText, lastPartialText)
    }

    private fun commitPartialToAccumulated() {
        val merged = currentDisplayText()
        if (merged.isNotBlank()) {
            accumulatedText = merged
        }
        lastPartialText = ""
    }

    private fun deliverFinal(text: String) {
        if (resultDelivered) return
        resultDelivered = true
        cancelClientStopFallback()
        cancelRestart()
        finishListening()
        val merged = mergeSpeechSegments(accumulatedText, text)
        Log.i(TAG, "final result (${merged.length} chars, continuations=$continuationCount): $merged")
        onFinalText?.invoke(merged)
    }

    private fun deliverPartialRecovery(text: String) {
        if (resultDelivered) return
        resultDelivered = true
        cancelClientStopFallback()
        cancelRestart()
        finishListening()
        val recovery = onPartialRecovery
        if (recovery != null) {
            recovery.invoke(text)
        } else {
            onFinalText?.invoke(text)
        }
    }

    private fun deliverError(error: Int) {
        if (resultDelivered) return

        if (shouldContinueListening(error)) {
            Log.w(
                TAG,
                "cloud timeout while holding, continue listening " +
                    "(#${continuationCount + 1}, partial=${currentDisplayText().take(40)})"
            )
            commitPartialToAccumulated()
            restartListeningSilently()
            return
        }

        val merged = currentDisplayText()
        if (merged.isNotEmpty() && isRecoverableError(error)) {
            if (userRequestedStop) {
                deliverFinal(merged)
            } else {
                deliverPartialRecovery(merged)
            }
            return
        }

        resultDelivered = true
        cancelClientStopFallback()
        cancelRestart()
        finishListening()
        Log.e(TAG, "error ${errorName(error)}, partial=${currentDisplayText().take(40)}")
        onError?.invoke(mapError(error))
    }

    /** 按住期间云端超时/短暂无匹配：静默续听，已听到的内容保留在 accumulatedText。 */
    private fun shouldContinueListening(error: Int): Boolean {
        if (userRequestedStop || lastIntent == null || isRestarting) return false
        val maxContinuations = if (shortAnswerMode) MAX_SHORT_ANSWER_CONTINUATIONS else MAX_CONTINUATIONS
        if (continuationCount >= maxContinuations) return false
        return when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK -> true
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> shortAnswerMode && currentDisplayText().isEmpty()
            else -> false
        }
    }

    private fun restartListeningSilently() {
        val intent = lastIntent ?: return
        continuationCount++
        cancelRestart()
        isRestarting = true
        lastPartialText = ""
        speechRecognizer?.cancel()
        restartRunnable = Runnable {
            if (resultDelivered || userRequestedStop) {
                isRestarting = false
                return@Runnable
            }
            isRestarting = false
            speechRecognizer?.startListening(intent)
        }
        mainHandler.postDelayed(restartRunnable!!, RESTART_DELAY_MS)
    }

    private fun scheduleClientStopFallback() {
        cancelClientStopFallback()
        val delayMs = if (shortAnswerMode) SHORT_CLIENT_STOP_FALLBACK_MS else CLIENT_STOP_FALLBACK_MS
        clientStopFallbackRunnable = Runnable {
            if (resultDelivered) return@Runnable
            val text = currentDisplayText()
            if (text.isNotEmpty()) {
                deliverFinal(text)
            } else {
                deliverError(SpeechRecognizer.ERROR_NO_MATCH)
            }
        }
        mainHandler.postDelayed(clientStopFallbackRunnable!!, delayMs)
    }

    private fun finishListening() {
        listening = false
        userRequestedStop = false
        isRestarting = false
        onListeningChanged?.invoke(false)
    }

    private fun isRecoverableError(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isRestarting = false
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                if (isRestarting) return
                if (userRequestedStop) {
                    userRequestedStop = false
                    scheduleClientStopFallback()
                    return
                }
            }
            deliverError(error)
        }

        override fun onResults(results: Bundle?) {
            val text = extractText(results)
            if (text.isNotEmpty()) {
                if (userRequestedStop) {
                    deliverFinal(text)
                } else {
                    accumulatedText = mergeSpeechSegments(accumulatedText, text)
                    lastPartialText = ""
                    onPartialText?.invoke(accumulatedText)
                }
                return
            }

            val merged = currentDisplayText()
            if (merged.isNotEmpty()) {
                if (userRequestedStop) {
                    deliverFinal(merged)
                } else {
                    onPartialText?.invoke(merged)
                }
            } else {
                deliverError(SpeechRecognizer.ERROR_NO_MATCH)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = extractText(partialResults)
            if (text.isEmpty()) return
            lastPartialText = text
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "partial: ${currentDisplayText().take(60)}")
            }
            onPartialText?.invoke(currentDisplayText())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorName(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "ERROR_UNKNOWN($error)"
        }
    }

    private fun mapError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "录音出错，请检查麦克风权限"
            SpeechRecognizer.ERROR_CLIENT -> "语音识别已取消"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
            SpeechRecognizer.ERROR_NETWORK -> "网络不稳定，请稍后再试"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "识别超时了，请按住按钮再说一遍，或分成两句短话"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请按住按钮再说一遍"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正忙，请稍后再试"
            SpeechRecognizer.ERROR_SERVER -> "语音服务异常，请稍后再试"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到说话，请按住按钮再说"
            else -> "语音识别失败，请重试"
        }
    }
}

internal fun mergeSpeechSegments(base: String, segment: String): String {
    val left = base.trim()
    val right = segment.trim()
    if (left.isEmpty()) return right
    if (right.isEmpty()) return left
    if (right.startsWith(left)) return right
    if (left.startsWith(right)) return left

    val maxOverlap = minOf(left.length, right.length)
    for (length in maxOverlap downTo 1) {
        if (left.regionMatches(left.length - length, right, 0, length, ignoreCase = false)) {
            return left + right.substring(length)
        }
    }
    return left + right
}
