package com.littlehelper.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 本地录音管理器，基于 [AudioRecord]，输出标准的 .wav (PCM 16-bit 16kHz 单声道) 文件。
 * 这种格式对所有云端 ASR 服务（包括火山引擎）兼容性最好。
 */
class AudioRecorderManager(private val context: Context) {

    enum class State { IDLE, RECORDING, FINISHED }

    var state: State = State.IDLE
        private set

    var onStateChanged: ((State) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    /** 录音过程中推送 PCM 块（16kHz mono 16-bit），供流式 ASR 使用。 */
    var onPcmChunk: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (state == State.RECORDING) return true
        cleanup(deleteFile = true)

        val file = File(context.cacheDir, "lh_rec_${System.currentTimeMillis()}.wav")
        outputFile = file

        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            audioRecord?.startRecording()
            setState(State.RECORDING)
            Log.i(TAG, "start → ${file.name}")

            recordingJob = scope.launch {
                writeAudioDataToFile(file)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            cleanup(deleteFile = true)
            onError?.invoke("录音启动失败，请检查麦克风权限")
            false
        }
    }

    private fun writeAudioDataToFile(file: File) {
        val data = ByteArray(bufferSize)
        var totalAudioLen = 0L

        try {
            FileOutputStream(file).use { os ->
                // 写入占位的 WAV 头 (44 字节)
                writeWavHeader(os, 0, 0, sampleRate, 1, 16)

                while (state == State.RECORDING && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        os.write(data, 0, read)
                        totalAudioLen += read
                        onPcmChunk?.invoke(data.copyOf(read))
                    }
                }
            }
            // 录音结束后，修正 WAV 头中的真实文件大小
            updateWavHeader(file, totalAudioLen)
        } catch (e: Exception) {
            Log.e(TAG, "writeAudioDataToFile failed", e)
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        val byteRate = longSampleRate * channels * bitsPerSample / 8
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File, totalAudioLen: Long) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalDataLen = totalAudioLen + 36

                // Update RIFF chunk size
                raf.seek(4)
                raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen.toInt()).array())

                // Update data chunk size
                raf.seek(40)
                raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen.toInt()).array())
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateWavHeader failed", e)
        }
    }

    fun stop(): File? {
        if (state != State.RECORDING) {
            return outputFile?.takeIf { it.exists() && it.length() > 44 }
        }
        return runBlocking {
            stopInternal()
        }
    }

    /** 在协程中调用，避免主线程阻塞导致 ANR。 */
    suspend fun stopAsync(): File? = withContext(Dispatchers.IO) {
        stopInternal()
    }

    private suspend fun stopInternal(): File? {
        try {
            setState(State.FINISHED)
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop failed", e)
        }

        recordingJob?.join()

        releaseRecorder()

        val file = outputFile
        if (file == null || !file.exists() || file.length() <= 44L) {
            file?.delete()
            outputFile = null
            setState(State.IDLE)
            onError?.invoke("没有录到声音，请按住按钮重新说一遍")
            return null
        }
        Log.i(TAG, "stop ok → ${file.length()} bytes")
        return file
    }

    fun cancel() {
        cleanup(deleteFile = true)
        setState(State.IDLE)
    }

    fun destroy() {
        cancel()
        scope.cancel()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun releaseRecorder() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
    }

    private fun cleanup(deleteFile: Boolean) {
        recordingJob?.cancel()
        releaseRecorder()
        onPcmChunk = null
        if (deleteFile) {
            outputFile?.delete()
            outputFile = null
        }
    }

    private fun setState(newState: State) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    companion object {
        private const val TAG = "LHAudioRecorder"
    }
}
