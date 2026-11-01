package com.ramka.app.yggmail

/**
 * Почтовый ввод-вывод канала. Ни один метод не бросает исключений наружу (кроме отмены корутины): любая
 * ошибка сети, TLS, авторизации — это `Result.failure`.
 */
interface YggmailMail {
    /** Отправляет [payload] (непрозрачные запечатанные байты) письмом с темой «Ramka-E2EE» на [recipient]. */
    suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit>

    /**
     * Читает непрочитанные письма «Ramka-E2EE» и для каждого вызывает [handler] с Base64-телом. Письмо помечается
     * прочитанным ТОЛЬКО если [handler] вернул true (иначе останется непрочитанным до следующего опроса).
     * @return сколько писем обработано и помечено прочитанными.
     */
    suspend fun receive(config: YggmailConfig, handler: suspend (base64Body: String) -> Boolean): Result<Int>

    /** Проверка настроек: вход на SMTP и на IMAP без отправки и чтения писем. */
    suspend fun test(config: YggmailConfig): Result<Unit>
}

/**
 * Вариант `receive()` из задания: возвращает Base64-строки всех непрочитанных писем «Ramka-E2EE» и помечает их
 * прочитанными. Для рабочего приёма используется [YggmailMail.receive] с подтверждением — так письмо не пропадёт,
 * если обработать его сейчас нечем.
 */
suspend fun YggmailMail.receiveBase64(config: YggmailConfig): Result<List<String>> {
    val bodies = mutableListOf<String>()
    return receive(config) { body ->
        bodies += body
        true
    }.map { bodies.toList() }
}
