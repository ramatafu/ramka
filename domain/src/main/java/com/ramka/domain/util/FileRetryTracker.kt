package com.ramka.domain.util

/**
 * Счётчик автоматических повторов отправки файлов (по messageId), только в памяти: переживает пересоздание
 * ViewModel, но не перезапуск процесса (после него остаётся ручное «Повторить»). Потокобезопасен.
 */
class FileRetryTracker(private val maxAutoRetries: Int = FileTransferConfig.MAX_AUTO_RETRIES) {
    private val attempts = HashMap<String, Int>()

    /** Занимает ещё одну автопопытку. @return false, если лимит уже исчерпан. */
    @Synchronized
    fun tryAcquire(messageId: String): Boolean {
        val used = attempts[messageId] ?: 0
        if (used >= maxAutoRetries) return false
        attempts[messageId] = used + 1
        return true
    }

    /** Ручной запуск или успешная отправка: счёт начинается заново. */
    @Synchronized
    fun reset(messageId: String) {
        attempts.remove(messageId)
    }

    @Synchronized
    fun attempts(messageId: String): Int = attempts[messageId] ?: 0
}
