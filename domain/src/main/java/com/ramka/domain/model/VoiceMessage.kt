package com.ramka.domain.model

/**
 * Голосовое сообщение — типизированный ВИД файлового сообщения [MessageBody.File], а не отдельный подкласс.
 *
 * Технически это обычный файл (Opus в Ogg; на Android 8–9, где MediaRecorder не умеет Opus, — AAC в m4a),
 * поэтому вся файловая машинерия работает без изменений: передача чанками по тому же зашифрованному каналу,
 * прогресс, «Повторить», хранение принятого в зашифрованном виде, удаление файла вместе с сообщением,
 * схема БД и формат пакетов. Признак голосового — имя `voice-<секунды>s.ogg|m4a` и MIME `audio/ogg|audio/mp4`;
 * длительность едет в имени (оно внутри зашифрованного пакета), так что получателю не нужно ничего декодировать.
 *
 * @property uri локальный URI файла: null, пока он не получен целиком (то же, что у [MessageBody.File.uri]).
 */
data class VoiceMessage(
    val durationSeconds: Int,
    val fileSize: Long,
    val uri: String?
)

object VoiceMessages {
    const val MAX_DURATION_SECONDS = 300
    const val MIN_DURATION_SECONDS = 1
    const val MIME_OPUS = "audio/ogg"
    const val MIME_AAC = "audio/mp4"

    private val NAME_REGEX = Regex("^voice-(\\d{1,3})s\\.(ogg|m4a)$")

    fun fileName(durationSeconds: Int, opus: Boolean): String =
        "voice-${durationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)}s.${if (opus) "ogg" else "m4a"}"

    fun mimeType(opus: Boolean): String = if (opus) MIME_OPUS else MIME_AAC

    /** Разбирает файловое сообщение как голосовое; null — это обычный файл. */
    fun parse(body: MessageBody.File): VoiceMessage? {
        val match = NAME_REGEX.matchEntire(body.fileName) ?: return null
        val seconds = match.groupValues[1].toInt()
        val extension = match.groupValues[2]
        val expectedMime = if (extension == "ogg") MIME_OPUS else MIME_AAC
        if (body.mimeType != expectedMime) return null
        if (seconds !in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) return null
        return VoiceMessage(durationSeconds = seconds, fileSize = body.fileSize, uri = body.uri)
    }

    /** «0:45», «5:00». Отрицательные значения считаются нулём. */
    fun formatDuration(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

fun MessageBody.File.asVoice(): VoiceMessage? = VoiceMessages.parse(this)
