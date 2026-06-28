package com.littlehelper.tts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.lang.ref.WeakReference
import java.util.Locale

class TtsManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var boundActivity: WeakReference<Activity>? = null

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var isInitializing = false
    private var pendingText: String? = null
    private var onDoneCallback: (() -> Unit)? = null
    var onSpeakStart: (() -> Unit)? = null

    private val enginePackages = mutableListOf<String?>()

    init {
        bindActivityIfPossible(context)
        initTextToSpeech()
    }

    fun bindActivity(activity: Activity) {
        boundActivity = WeakReference(activity)
        if (!isReady && !isInitializing) {
            mainHandler.postDelayed({ initTextToSpeech() }, 200)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!isReady) {
            pendingText = text
            onDoneCallback = onDone
            if (!isInitializing) initTextToSpeech()
            return
        }
        onDoneCallback = onDone
        ensureChineseLocale()
        requestAudioFocus()
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "little_helper_tts")
    }

    fun stop() {
        pendingText = null
        onDoneCallback = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun bindActivityIfPossible(context: Context) {
        if (context is Activity) {
            boundActivity = WeakReference(context)
        }
    }

    private fun initTextToSpeech() {
        if (isInitializing || isReady) return
        isInitializing = true
        collectEngines()
        tryEngine(0)
    }

    private fun collectEngines() {
        enginePackages.clear()
        val defaultEngine = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH
        )
        if (!defaultEngine.isNullOrEmpty()) {
            enginePackages.add(defaultEngine)
        }
        enginePackages.add(null)

        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services: List<ResolveInfo> =
            appContext.packageManager.queryIntentServices(intent, 0)
        for (info in services) {
            val pkg = info.serviceInfo.packageName
            if (!enginePackages.contains(pkg)) {
                enginePackages.add(pkg)
            }
        }
    }

    private fun tryEngine(index: Int) {
        if (index >= enginePackages.size) {
            isInitializing = false
            return
        }

        tts?.stop()
        tts?.shutdown()
        tts = null

        val enginePackage = enginePackages[index]
        val initContext = if (enginePackage == null) {
            boundActivity?.get() ?: appContext
        } else {
            appContext
        }

        val listener = TextToSpeech.OnInitListener { status ->
            onEngineInit(status, index)
        }

        tts = if (enginePackage == null) {
            TextToSpeech(initContext, listener)
        } else {
            TextToSpeech(appContext, listener, enginePackage)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post { onSpeakStart?.invoke() }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        onDoneCallback?.invoke()
                        onDoneCallback = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        onDoneCallback?.invoke()
                        onDoneCallback = null
                    }
                }
            })
        }
    }

    private fun onEngineInit(status: Int, index: Int) {
        if (status != TextToSpeech.SUCCESS) {
            mainHandler.post { tryEngine(index + 1) }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }

        if (!bindChineseLocale()) {
            mainHandler.post { tryEngine(index + 1) }
            return
        }

        tts?.setSpeechRate(0.95f)
        isReady = true
        isInitializing = false

        pendingText?.let {
            pendingText = null
            speak(it, onDoneCallback)
        }
    }

    private fun bindChineseLocale(): Boolean {
        val engine = tts ?: return false
        var result = engine.setLanguage(Locale.CHINA)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            selectChineseVoice(engine)
        }
        return true
    }

    private fun selectChineseVoice(engine: TextToSpeech) {
        val voices = engine.voices ?: return
        val preferred = voices.firstOrNull { voice ->
            !voice.isNetworkConnectionRequired &&
                Locale.CHINA.language == voice.locale?.language &&
                Locale.CHINA.country == voice.locale?.country
        } ?: voices.firstOrNull { voice ->
            !voice.isNetworkConnectionRequired &&
                Locale.CHINA.language == voice.locale?.language
        }
        preferred?.let { engine.setVoice(it) }
    }

    private fun ensureChineseLocale() {
        if (isReady) bindChineseLocale()
    }

    private fun requestAudioFocus() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }
}
