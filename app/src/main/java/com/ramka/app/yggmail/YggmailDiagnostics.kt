package com.ramka.app.yggmail

/**
 * Журнал обмена с почтовым сервером для экрана настроек: что именно сказал сервер при «Проверить».
 * Заполняется только во время проверки настроек (обычная отправка и приём ничего сюда не пишут).
 *
 * Пароль и данные авторизации в журнал не попадают: JavaMail сам подавляет команды входа
 * (`mail.debug.auth=false`), а здесь дополнительно вычищается сам пароль и любые длинные Base64-токены в строках про
 * вход. Хранится в памяти последние [MAX_LINES] строк.
 */
object YggmailDiagnostics {
    private const val MAX_LINES = 120
    private const val MAX_LINE_LENGTH = 300
    private val TOKEN = Regex("[A-Za-z0-9+/=]{24,}")
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun clear() = lines.clear()

    /** Собственная заметка приложения (этап проверки, выбранный способ входа). */
    @Synchronized
    fun note(line: String) = append(line)

    /** Строка отладочного вывода JavaMail; [secrets] (пароль) в ней заменяются на `***`. */
    @Synchronized
    fun addDebug(raw: String, secrets: List<String>) = append(redact(raw, secrets))

    @Synchronized
    fun lines(): List<String> = lines.toList()

    internal fun redact(raw: String, secrets: List<String>): String {
        var line = raw
        for (secret in secrets) {
            if (secret.isNotEmpty()) line = line.replace(secret, "***")
        }
        if (line.contains("AUTH", ignoreCase = true) || line.contains("LOGIN", ignoreCase = true)) {
            line = TOKEN.replace(line, "<скрыто>")
        }
        return line
    }

    private fun append(line: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast(line.take(MAX_LINE_LENGTH))
    }
}
