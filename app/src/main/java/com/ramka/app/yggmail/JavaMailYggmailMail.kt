package com.ramka.app.yggmail

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import java.util.UUID
import javax.mail.AuthenticationFailedException
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.AndTerm
import javax.mail.search.FlagTerm
import javax.mail.search.SubjectTerm
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Реализация [YggmailMail] на JavaMail (`com.sun.mail:android-mail`). Всё выполняется на `Dispatchers.IO`, UI не
 * блокируется; блокирующие операции ограничены таймаутами сокета. Любое исключение превращается в `Result.failure`,
 * в лог пишется только имя класса ошибки (без адресов, логина, пароля и содержимого).
 *
 * Логин и пароль уходят на сервер ровно в том виде, как введены (логин — без пробелов по краям); приложение
 * ничего к ним не дописывает.
 *
 * Способ входа:
 *  * SMTP — ТОЛЬКО `AUTH PLAIN` (`\0логин\0пароль`). JavaMail по умолчанию пробует `AUTH LOGIN` раньше `PLAIN`, а
 *    SMTP-сервер Yggmail (версия от 30.05.2026) на `AUTH LOGIN` падает с паникой (nil pointer в
 *    `smtpserver.Backend.Login`), тогда как `AUTH PLAIN` работает. Канал рассчитан именно на Yggmail, поэтому
 *    `LOGIN` не разрешён вообще: сервер без `PLAIN` для Ramka недоступен.
 *  * IMAP — сначала обычная команда `LOGIN` (та же, что проверяется вручную через `nc`), и только если она не
 *    подошла — механизмы `AUTH=PLAIN`/`AUTH=LOGIN` по умолчанию.
 *
 * @param debugLog подробный обмен с сервером (только при «Проверить»; пароль скрыт) — в Logcat.
 */
class JavaMailYggmailMail(
    private val log: (String) -> Unit = {},
    private val debugLog: (String) -> Unit = {}
) : YggmailMail {

    override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> =
        guarded("send") {
            val session = newSession(config, loginCommandOnly = false, debug = false)
            val message = object : MimeMessage(session) {
                // Message-ID по умолчанию содержит имя/адрес этого устройства — подменяем случайным.
                override fun updateMessageID() {
                    setHeader("Message-ID", "<${UUID.randomUUID()}@yggmail>")
                }
            }.apply {
                setFrom(InternetAddress(config.login))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
                setSubject(YggmailPayload.SUBJECT, "UTF-8")
                setText(YggmailPayload.encode(payload), "UTF-8")
            }
            val transport = openSmtp(session, config)
            try {
                transport.sendMessage(message, message.allRecipients)
            } finally {
                runCatching { transport.close() }
            }
        }

    override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> =
        guarded("receive") {
            val store = openImap(config, debug = false)
            var handled = 0
            try {
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_WRITE)
                try {
                    val unreadRamka = AndTerm(FlagTerm(Flags(Flags.Flag.SEEN), false), SubjectTerm(YggmailPayload.SUBJECT))
                    // SubjectTerm ищет подстроку, поэтому точное совпадение темы проверяем ниже сами.
                    for (message in inbox.search(unreadRamka).take(MAX_MESSAGES_PER_POLL)) {
                        if (message.subject != YggmailPayload.SUBJECT) continue
                        val body = readText(message)
                        // Нечитаемое или огромное письмо — не наше: помечаем прочитанным, чтобы не разбирать снова.
                        val accepted = body != null && body.length <= YggmailPayload.MAX_BODY_CHARS && handler(body)
                        if (accepted || body == null || body.length > YggmailPayload.MAX_BODY_CHARS) {
                            message.setFlag(Flags.Flag.SEEN, true)
                            if (accepted) handled++
                        }
                    }
                } finally {
                    runCatching { inbox.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
            handled
        }

    /**
     * Проверка настроек по шагам: сначала SMTP, потом IMAP. Ошибка помечается тем шагом, на котором произошла
     * ([YggmailStageException]), а подробный обмен с сервером (без пароля) попадает в [YggmailDiagnostics] и Logcat.
     */
    override suspend fun test(config: YggmailConfig): Result<Unit> = guarded("test") {
        YggmailDiagnostics.clear()
        YggmailDiagnostics.note("SMTP: порт ${config.smtpPort}, защита ${MailSecurityPolicy.forSmtp(config.smtpPort)}")
        stage("SMTP") {
            val transport = openSmtp(newSession(config, loginCommandOnly = false, debug = true), config)
            runCatching { transport.close() }
        }
        YggmailDiagnostics.note("SMTP: вход выполнен")
        YggmailDiagnostics.note("IMAP: порт ${config.imapPort}, защита ${MailSecurityPolicy.forImap(config.imapPort)}")
        stage("IMAP") {
            val store = openImap(config, debug = true)
            try {
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                runCatching { inbox.close(false) }
            } finally {
                runCatching { store.close() }
            }
        }
        YggmailDiagnostics.note("IMAP: вход выполнен")
    }

    // ------------------------------------------------------------------ подключение

    private fun openSmtp(session: Session, config: YggmailConfig): Transport {
        val security = MailSecurityPolicy.forSmtp(config.smtpPort)
        val transport = session.getTransport(if (security == MailSecurity.SSL) "smtps" else "smtp")
        try {
            transport.connect(MailHost.formatIPv6ForJavaMail(config.smtpHost), config.smtpPort, config.login, config.password)
        } catch (e: Exception) {
            runCatching { transport.close() }
            throw e
        }
        return transport
    }

    /**
     * Вход на IMAP: сначала простой командой `LOGIN` (как через `nc`), затем механизмами по умолчанию. JavaMail без
     * этого предпочитает `AUTHENTICATE PLAIN`, и некоторые серверы, у которых обычный `LOGIN` работает, его отклоняют.
     * Сетевые ошибки не повторяются — только отказы авторизации.
     */
    private fun openImap(config: YggmailConfig, debug: Boolean): Store {
        val security = MailSecurityPolicy.forImap(config.imapPort)
        var failure: Exception? = null
        for (loginCommandOnly in listOf(true, false)) {
            if (debug) YggmailDiagnostics.note(if (loginCommandOnly) "IMAP: вход командой LOGIN" else "IMAP: вход механизмами по умолчанию (AUTH=PLAIN и др.)")
            val session = newSession(config, loginCommandOnly, debug)
            val store = session.getStore(if (security == MailSecurity.SSL) "imaps" else "imap")
            try {
                store.connect(MailHost.formatIPv6ForJavaMail(config.imapHost), config.imapPort, config.login, config.password)
                return store
            } catch (e: AuthenticationFailedException) {
                runCatching { store.close() }
                failure = e
            } catch (e: MessagingException) {
                runCatching { store.close() }
                if (e.cause is IOException) throw e // сеть/TLS: второй попыткой не лечится
                failure = e // например «No login methods supported!» — пробуем следующий способ
            }
        }
        throw failure ?: IllegalStateException("IMAP: нет способа входа")
    }

    /** Ошибка шага проверки помечается названием шага. */
    private inline fun <T> stage(name: String, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw YggmailStageException(name, e)
    }

    // ------------------------------------------------------------------ внутреннее

    /**
     * JavaMail ищет провайдеров протоколов и обработчики MIME через контекстный загрузчик классов потока; в потоке из
     * пула Android он иной, и без этого падает «No provider for imap» / «no object DCH for MIME type text/plain».
     */
    private suspend fun <T> guarded(operation: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            thread.contextClassLoader = JavaMailYggmailMail::class.java.classLoader
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Yggmail $operation: ${e.javaClass.simpleName}")
                Result.failure(e)
            } catch (e: LinkageError) {
                // Классы JavaMail недоступны (например вырезаны сборкой): канал просто не работает, приложение живёт.
                log("Yggmail $operation: ${e.javaClass.simpleName}")
                Result.failure(IllegalStateException("JavaMail недоступен", e))
            } finally {
                thread.contextClassLoader = previous
            }
        }

    private fun newSession(config: YggmailConfig, loginCommandOnly: Boolean, debug: Boolean): Session {
        val session = Session.getInstance(sessionProperties(config, loginCommandOnly))
        if (debug) {
            session.debug = true
            session.debugOut = PrintStream(
                LineSink { line ->
                    YggmailDiagnostics.addDebug(line, listOf(config.password))
                    debugLog(YggmailDiagnostics.lines().lastOrNull().orEmpty())
                },
                true
            )
        }
        return session
    }

    internal fun sessionProperties(config: YggmailConfig, loginCommandOnly: Boolean): Properties = Properties().apply {
        val smtpSecurity = MailSecurityPolicy.forSmtp(config.smtpPort)
        val imapSecurity = MailSecurityPolicy.forImap(config.imapPort)
        put("mail.debug.auth", "false") // команды входа (логин, пароль) в отладочный вывод не попадают
        for (protocol in listOf("smtp", "smtps")) {
            put("mail.$protocol.auth", "true")
            put("mail.$protocol.connectiontimeout", TIMEOUT_MS)
            put("mail.$protocol.timeout", TIMEOUT_MS)
            put("mail.$protocol.writetimeout", TIMEOUT_MS)
            put("mail.$protocol.localhost", "ramka") // в приветствии EHLO не светим имя и адрес устройства
            // Только PLAIN: SMTP Yggmail падает с паникой на AUTH LOGIN (см. описание класса).
            put("mail.$protocol.auth.mechanisms", "PLAIN")
            // Пустой authzid: на сервер уходит ровно `\0логин\0пароль`, как при ручной проверке.
            put("mail.$protocol.sasl.authorizationid", "")
            if (smtpSecurity == MailSecurity.STARTTLS) {
                put("mail.$protocol.starttls.enable", "true")
                put("mail.$protocol.starttls.required", "true")
            }
        }
        for (protocol in listOf("imap", "imaps")) {
            put("mail.$protocol.connectiontimeout", TIMEOUT_MS)
            put("mail.$protocol.timeout", TIMEOUT_MS)
            put("mail.$protocol.writetimeout", TIMEOUT_MS)
            put("mail.$protocol.peek", "true") // чтение тела не должно само ставить «прочитано»
            if (imapSecurity == MailSecurity.STARTTLS) {
                put("mail.$protocol.starttls.enable", "true")
                put("mail.$protocol.starttls.required", "true")
            }
            if (loginCommandOnly) {
                // Отключаем AUTHENTICATE PLAIN/LOGIN/NTLM/XOAUTH2: JavaMail остаётся только простая команда LOGIN.
                put("mail.$protocol.auth.plain.disable", "true")
                put("mail.$protocol.auth.login.disable", "true")
                put("mail.$protocol.auth.ntlm.disable", "true")
                put("mail.$protocol.auth.xoauth2.disable", "true")
            }
        }
    }

    private fun readText(part: Part): String? = try {
        when (val content = part.content) {
            is String -> content
            is InputStream -> content.use { String(it.readBytes(), Charsets.UTF_8) }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /** Собирает вывод JavaMail по строкам. */
    private class LineSink(private val onLine: (String) -> Unit) : OutputStream() {
        private val buffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            when (b) {
                '\n'.code -> flushLine()
                '\r'.code -> Unit
                else -> buffer.write(b)
            }
        }

        private fun flushLine() {
            if (buffer.size() == 0) return
            val line = buffer.toString(Charsets.UTF_8.name())
            buffer.reset()
            onLine(line)
        }
    }

    private companion object {
        const val TIMEOUT_MS = "15000"
        const val MAX_MESSAGES_PER_POLL = 50
    }
}

/** Ошибка конкретного шага проверки настроек («SMTP» или «IMAP»). */
class YggmailStageException(val stage: String, cause: Throwable) : Exception(cause)

/** Короткое объяснение ошибки для экрана настроек: с ответом сервера, но без адресов и учётных данных. */
object YggmailErrors {
    private const val MAX_SERVER_TEXT = 160

    fun describe(error: Throwable, depth: Int = 0): String = when {
        error is YggmailStageException -> "${error.stage}: ${describe(error.cause ?: error, depth + 1)}"
        // Раньше здесь всегда было «Неверный логин или пароль» — это скрывало настоящую причину; теперь показываем ответ сервера.
        error is AuthenticationFailedException -> "Сервер отклонил вход" + serverText(error)
        error is UnknownHostException -> "Адрес сервера не найден"
        error is SocketTimeoutException -> "Сервер не отвечает (таймаут)"
        error is ConnectException -> "Не удалось подключиться к серверу: проверьте адрес, порт и что Yggdrasil запущен"
        error is SSLException -> "Ошибка TLS: сертификат сервера не принят"
        error is MessagingException && error.cause != null && depth < 4 -> describe(error.cause!!, depth + 1)
        error is MessagingException -> "Ошибка почты" + serverText(error)
        error is IllegalStateException && error.cause is LinkageError -> "Библиотека почты недоступна в этой сборке"
        else -> "Ошибка соединения (${error.javaClass.simpleName})"
    }

    private fun serverText(error: Throwable): String =
        error.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": " + it.take(MAX_SERVER_TEXT) }.orEmpty()
}
