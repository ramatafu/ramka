package com.ramka.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Запись голосового сообщения через стандартный MediaRecorder: Opus в Ogg (Android 10+) или AAC в m4a
 * (Android 8–9, где Opus-энкодера у MediaRecorder нет). Моно 16 кГц, 24–32 кбит/с: 5 минут ≈ 1 МБ.
 *
 * Файл пишется во внутренний кэш (cacheDir/voice_tmp), наружу не попадает. На время записи берётся
 * аудиофокус: если его забрал входящий звонок или другое приложение, вызывается `onInterrupted`.
 * Объект не потокобезопасен: все вызовы — с главного потока (из ViewModel).
 */
class VoiceRecorder(private val context: Context) {

    class Recording(val file: File, val durationSeconds: Int, val opus: Boolean)

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAtMs = 0L
    private var opus = false
    private var focusRequest: AudioFocusRequest? = null

    /** @return true, если запись пошла; false — микрофон занят/недоступен или не удалось создать файл. */
    fun start(onInterrupted: () -> Unit): Boolean {
        if (recorder != null) return false
        val dir = File(context.cacheDir, TMP_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val file = File(dir, UUID.randomUUID().toString() + if (opus) ".ogg" else ".m4a")

        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (opus) {
                media.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                media.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                media.setAudioEncodingBitRate(OPUS_BITRATE)
            } else {
                media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                media.setAudioEncodingBitRate(AAC_BITRATE)
            }
            media.setAudioChannels(1)
            media.setAudioSamplingRate(SAMPLE_RATE)
            media.setOutputFile(file.absolutePath)
            media.setOnErrorListener { _, what, _ ->
                Log.w(TAG, "MediaRecorder error: $what")
                onInterrupted()
            }
            media.prepare()
            requestFocus(onInterrupted)
            media.start()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось начать запись: ${e.javaClass.simpleName}")
            runCatching { media.release() }
            file.delete()
            abandonFocus()
            return false
        }
        recorder = media
        output = file
        startedAtMs = SystemClock.elapsedRealtime()
        return true
    }

    /** Останавливает запись и отдаёт готовый файл; null — запись не удалась (слишком короткая, прервана). */
    fun stop(): Recording? {
        val media = recorder ?: return null
        val file = output
        recorder = null
        output = null
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        abandonFocus()
        try {
            media.stop()
        } catch (e: RuntimeException) {
            // MediaRecorder бросает при остановке почти пустой записи — файл невалиден.
            file?.delete()
            return null
        } finally {
            runCatching { media.release() }
        }
        if (file == null || !file.isFile || file.length() == 0L) {
            file?.delete()
            return null
        }
        return Recording(file, ((elapsedMs + 500) / 1000).toInt(), opus)
    }

    /** Останавливает запись и удаляет файл. Безопасно вызывать без активной записи. */
    fun cancel() {
        val media = recorder
        val file = output
        recorder = null
        output = null
        abandonFocus()
        if (media != null) {
            runCatching { media.stop() }
            runCatching { media.release() }
        }
        file?.delete()
    }

    private fun requestFocus(onLost: () -> Unit) {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLost()
            }
            .build()
        // Результат не проверяем: отказ в фокусе не повод отказывать в записи.
        manager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
    }

    private companion object {
        const val TAG = "ramka"
        const val TMP_DIR = "voice_tmp"
        const val SAMPLE_RATE = 16_000
        const val OPUS_BITRATE = 24_000
        const val AAC_BITRATE = 32_000
    }
}
