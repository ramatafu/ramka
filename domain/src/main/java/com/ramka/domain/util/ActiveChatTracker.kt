package com.ramka.domain.util

/**
 * Какой чат сейчас виден пользователю (экран открыт и приложение на переднем плане).
 * Нужен, чтобы сообщения, пришедшие в уже открытый чат, не попадали в счётчик
 * непрочитанных — пользователь их видит сразу. Хранит только id контакта в памяти,
 * ничего не пишет на диск.
 */
class ActiveChatTracker {
    @Volatile
    private var activeContactId: String? = null

    fun onChatShown(contactId: String) { activeContactId = contactId }

    /** Сбрасывает только если скрывается именно этот чат (защита от гонки при переходах). */
    fun onChatHidden(contactId: String) {
        if (activeContactId == contactId) activeContactId = null
    }

    fun isOpen(contactId: String): Boolean = activeContactId == contactId
}
