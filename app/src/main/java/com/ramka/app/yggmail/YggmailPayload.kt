package com.ramka.app.yggmail

import java.util.Base64

/**
 * Формат письма канала: тема строго «Ramka-E2EE», тело — Base64 от запечатанного (уже зашифрованного) пакета.
 * Сам шифр сюда не входит: пакет приходит непрозрачными байтами от
 * [com.ramka.domain.util.OfflinePacketSealer].
 */
object YggmailPayload {
    const val SUBJECT = "Ramka-E2EE"

    /** Верхняя граница запечатанного пакета: только короткие сообщения; 64 КБ-кадры файлов сюда не идут. */
    const val MAX_SEALED_BYTES = 32 * 1024

    /** Верхняя граница тела письма (Base64 с переводами строк), всё больше отбрасывается без разбора. */
    const val MAX_BODY_CHARS = 64 * 1024

    private val BASE64_BODY = Regex("^[A-Za-z0-9+/=\\s]+$")

    /** Base64 строками по 76 символов, чтобы письмо проходило любые SMTP-серверы. */
    fun encode(sealed: ByteArray): String =
        Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(sealed)

    /** Тело письма → байты; null — это не наш формат (посторонние символы, пусто, слишком большое). */
    fun decode(body: String): ByteArray? {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_BODY_CHARS || !BASE64_BODY.matches(trimmed)) return null
        return try {
            Base64.getMimeDecoder().decode(trimmed).takeIf { it.isNotEmpty() && it.size <= MAX_SEALED_BYTES }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
