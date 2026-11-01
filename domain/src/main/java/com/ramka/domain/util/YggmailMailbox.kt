package com.ramka.domain.util

import com.ramka.domain.model.Contact

/**
 * Почтовый адрес контакта в Yggmail: `<локальная часть>@yggmail` (в настоящем Yggmail это публичный ключ в hex).
 * Домен строго `yggmail`: поле контакта нельзя использовать, чтобы заставить приложение слать письма на
 * произвольный внешний адрес через ваш SMTP-сервер.
 *
 * Поле `Contact.yggmailAddress` может содержать либо IPv6 сети Yggdrasil ([YggdrasilAddress], прямое TCP-соединение),
 * либо почтовый адрес Yggmail (этот класс, канал store-and-forward) — см. [ContactNetworkAddress].
 */
object YggmailMailbox {
    private const val DOMAIN = "yggmail"
    private val LOCAL_PART = Regex("^[A-Za-z0-9._-]{1,128}$")

    /** @return адрес в нижнем регистре или null, если запись не является адресом Yggmail. */
    fun parse(input: String): String? {
        val trimmed = input.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return null
        val local = trimmed.substring(0, at)
        val domain = trimmed.substring(at + 1)
        if (!domain.equals(DOMAIN, ignoreCase = true) || !LOCAL_PART.matches(local)) return null
        return "${local.lowercase()}@$DOMAIN"
    }

    fun isValid(input: String): Boolean = parse(input) != null

    /** Адрес получателя письма для контакта или null — у контакта нет почтового адреса Yggmail. */
    fun recipientFor(contact: Contact): String? = contact.yggmailAddress?.let { parse(it) }
}

/** Что можно ввести в поле «Yggmail-адрес» контакта: IPv6 Yggdrasil или почтовый адрес `…@yggmail`. */
object ContactNetworkAddress {
    fun isValid(input: String): Boolean = YggdrasilAddress.isValid(input) || YggmailMailbox.isValid(input)
}
