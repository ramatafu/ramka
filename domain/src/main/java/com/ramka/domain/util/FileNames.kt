package com.ramka.domain.util

/**
 * Очистка недоверенных имени файла и MIME-типа из сети (и из пикера). Результат безопасен
 * как отображаемое имя и как имя в MediaStore: без путей, управляющих и «служебных» символов.
 */
object FileNames {
    const val MAX_NAME_CHARS = 100
    const val DEFAULT_NAME = "file"
    const val DEFAULT_MIME_TYPE = "application/octet-stream"

    private const val FORBIDDEN = ":*?\"<>|"
    private const val MAX_EXTENSION_CHARS = 10
    private val MIME_REGEX = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,99}/[a-z0-9][a-z0-9!#$&^_.+-]{0,99}$")

    fun sanitizeFileName(raw: String?): String {
        if (raw == null) return DEFAULT_NAME
        val lastSegment = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = lastSegment
            .filter { !it.isISOControl() && it !in FORBIDDEN }
            .trim()
            .trim('.')
            .trim()
        if (cleaned.isEmpty()) return DEFAULT_NAME
        if (cleaned.length <= MAX_NAME_CHARS) return cleaned

        val ext = cleaned.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= MAX_EXTENSION_CHARS && cleaned.length > ext.length + 1) {
            val base = cleaned.dropLast(ext.length + 1)
            base.take(MAX_NAME_CHARS - ext.length - 1) + "." + ext
        } else {
            cleaned.take(MAX_NAME_CHARS)
        }
    }

    fun sanitizeMimeType(raw: String?): String {
        val lower = raw?.trim()?.lowercase() ?: return DEFAULT_MIME_TYPE
        return if (MIME_REGEX.matches(lower)) lower else DEFAULT_MIME_TYPE
    }
}
