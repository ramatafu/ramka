package com.ramka.app.call

/**
 * Хранит ОДНО входящее предложение звонка, пришедшее, пока у приложения нет экрана (нет CallViewModel):
 * после тапа по уведомлению Activity создаётся заново и забирает его через [take]. Устаревшее
 * (старше [ttlMillis]) отдавать нельзя — звонящий давно прекратил попытку, SDP протух.
 */
class PendingOfferStore<T>(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private class Entry<T>(val key: String, val value: T, val atMillis: Long)

    private var entry: Entry<T>? = null

    @Synchronized
    fun put(key: String, value: T) {
        entry = Entry(key, value, now())
    }

    /** Ключ (callId) сохранённого предложения, если оно ещё не устарело. */
    @Synchronized
    fun key(): String? = entry?.takeIf { !it.isExpired() }?.key

    /** Отдаёт предложение и очищает хранилище; для устаревшего вернёт null. */
    @Synchronized
    fun take(): T? {
        val current = entry ?: return null
        entry = null
        return if (current.isExpired()) null else current.value
    }

    /** Очищает хранилище, если там предложение с этим [key]. */
    @Synchronized
    fun clear(key: String) {
        if (entry?.key == key) entry = null
    }

    private fun Entry<T>.isExpired(): Boolean {
        val age = now() - atMillis
        return age < 0 || age >= ttlMillis
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 60_000L
    }
}
