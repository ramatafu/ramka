package com.ramka.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.ramka.app.files.AndroidIncomingFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Воспроизведение голосовых сообщений через стандартный MediaPlayer (громкость медиа, STREAM_MUSIC).
 *
 * Singleton, не привязан к экрану: если пользователь свернул приложение или вышел из чата, звук продолжается
 * (пока жив процесс). Одновременно играет одно сообщение: запуск другого останавливает текущее. Пауза
 * сохраняет позицию; по окончании плеер возвращается в начало и значок снова «Play».
 *
 * Принятые файлы лежат зашифрованными ([AndroidIncomingFileStore]): перед воспроизведением их расшифровывает
 * тот же механизм, что и при «Открыть». Все методы вызываются с главного потока.
 */
@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incomingFileStore: AndroidIncomingFileStore
) {
    /** [messageId] — localId сообщения, которое сейчас загружено в плеер; [playing] — идёт ли звук. */
    data class State(val messageId: String? = null, val playing: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)

    /** Короткие тексты для Snackbar («Не удалось воспроизвести…»). */
    val errors: Flow<String> = _errors.receiveAsFlow()

    private val mutex = Mutex()
    private var player: MediaPlayer? = null
    private var currentId: String? = null

    /** Play/Pause для сообщения [messageId]: то же самое — пауза/продолжение, другое — остановить текущее и начать это. */
    suspend fun toggle(messageId: String, uriString: String, fileName: String) {
        mutex.withLock {
            val existing = player
            if (existing != null && currentId == messageId) {
                try {
                    if (existing.isPlaying) {
                        existing.pause()
                        publish(playing = false)
                    } else {
                        existing.start()
                        publish(playing = true)
                    }
                } catch (e: IllegalStateException) {
                    releaseLocked()
                    _errors.trySend(PLAY_ERROR)
                }
                return@withLock
            }

            releaseLocked()
            val source = resolve(uriString, fileName)
            if (source == null) {
                _errors.trySend(PLAY_ERROR)
                return@withLock
            }
            val prepared = withContext(Dispatchers.IO) { createPrepared(source) }
            if (prepared == null) {
                _errors.trySend(PLAY_ERROR)
                return@withLock
            }
            prepared.setOnCompletionListener { onCompleted(it) }
            prepared.setOnErrorListener { mp, _, _ ->
                onError(mp)
                true
            }
            try {
                prepared.start()
            } catch (e: IllegalStateException) {
                runCatching { prepared.release() }
                _errors.trySend(PLAY_ERROR)
                return@withLock
            }
            player = prepared
            currentId = messageId
            publish(playing = true)
        }
    }

    /** Останавливает и освобождает плеер (запись нового сообщения, удаление проигрываемого). */
    fun stop() {
        releaseLocked()
    }

    fun stopIfPlaying(messageId: String) {
        if (currentId == messageId) releaseLocked()
    }

    private suspend fun resolve(uriString: String, fileName: String): Uri? =
        if (AndroidIncomingFileStore.isEncryptedUri(uriString)) {
            val transferId = AndroidIncomingFileStore.transferIdFrom(uriString)
            if (transferId == null) null
            else incomingFileStore.getDecryptedFileUri(transferId, fileName)?.let { Uri.parse(it) }
        } else {
            Uri.parse(uriString)
        }

    private fun createPrepared(uri: Uri): MediaPlayer? {
        val media = MediaPlayer()
        return try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // поток STREAM_MUSIC
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            media.setDataSource(context, uri)
            media.prepare()
            media
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось подготовить воспроизведение: ${e.javaClass.simpleName}")
            runCatching { media.release() }
            null
        }
    }

    private fun onCompleted(media: MediaPlayer) {
        if (media !== player) return
        // Сброс на начало: следующее нажатие Play проиграет сообщение заново.
        runCatching { media.seekTo(0) }
        publish(playing = false)
    }

    private fun onError(media: MediaPlayer) {
        if (media !== player) return
        releaseLocked()
        _errors.trySend(PLAY_ERROR)
    }

    private fun releaseLocked() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        currentId = null
        _state.value = State()
    }

    private fun publish(playing: Boolean) {
        _state.value = State(messageId = currentId, playing = playing)
    }

    private companion object {
        const val TAG = "ramka"
        const val PLAY_ERROR = "Не удалось воспроизвести сообщение"
    }
}
