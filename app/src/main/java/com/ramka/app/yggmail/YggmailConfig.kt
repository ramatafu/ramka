package com.ramka.app.yggmail

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Настройки почтового сервера Yggmail. Пароль — секрет: в логах и `toString()` его нет, хранится только в
 * [YggmailConfigStore] (EncryptedSharedPreferences / Android Keystore). Хосты могут быть доменными именами,
 * IPv4 или IPv6-адресами сети Yggdrasil (с квадратными скобками или без — см. [MailHost]).
 */
data class YggmailConfig(
    val smtpHost: String,
    val smtpPort: Int,
    val imapHost: String,
    val imapPort: Int,
    val login: String,
    val password: String
) {
    /** @return понятный текст ошибки для UI или null, если настройки пригодны для подключения. */
    fun validate(): String? = when {
        !MailHost.isValid(smtpHost) -> "Некорректный адрес SMTP-сервера"
        smtpPort !in PORT_RANGE -> "Порт SMTP должен быть от 1 до 65535"
        !MailHost.isValid(imapHost) -> "Некорректный адрес IMAP-сервера"
        imapPort !in PORT_RANGE -> "Порт IMAP должен быть от 1 до 65535"
        login.isBlank() || login.any { it.isWhitespace() } -> "Укажите логин без пробелов"
        password.isEmpty() -> "Укажите пароль"
        else -> null
    }

    fun isValid(): Boolean = validate() == null

    // Автоматический toString data class вывел бы логин и пароль — а он попадает в исключения, логи и отчёты о сбоях.
    override fun toString(): String = "YggmailConfig(smtp=<$smtpPort>, imap=<$imapPort>, login=<hidden>, password=<hidden>)"

    companion object {
        val PORT_RANGE = 1..65535
    }
}

/** Бинарный формат конфига для хранилища: версия + шесть полей. Любая порча при чтении даёт null, не исключение. */
object YggmailConfigCodec {
    private const val VERSION = 1

    fun encode(config: YggmailConfig): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeUTF(config.smtpHost)
            out.writeInt(config.smtpPort)
            out.writeUTF(config.imapHost)
            out.writeInt(config.imapPort)
            out.writeUTF(config.login)
            out.writeUTF(config.password)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): YggmailConfig? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readUnsignedByte() != VERSION) return null
            val smtpHost = input.readUTF()
            val smtpPort = input.readInt()
            val imapHost = input.readUTF()
            val imapPort = input.readInt()
            val login = input.readUTF()
            val password = input.readUTF()
            YggmailConfig(smtpHost, smtpPort, imapHost, imapPort, login, password)
        }
    } catch (e: Exception) {
        null
    }
}
