package com.ramka.app.yggmail

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Настоящий JavaMail против поддельного SMTP-сервера, который ведёт себя как Yggmail: объявляет `AUTH LOGIN PLAIN`,
 * а на `AUTH LOGIN` «падает» (обрывает соединение). Тест гарантирует, что Ramka отправляет только `AUTH PLAIN`.
 */
class JavaMailSmtpAuthTest {
    private val servers = mutableListOf<FakeSmtpServer>()

    @After fun tearDown() = servers.forEach { it.close() }

    private fun server(mechanisms: String = "LOGIN PLAIN"): FakeSmtpServer = FakeSmtpServer(mechanisms).also { servers += it }

    private fun config(port: Int, login: String = "me@yggmail", password: String = "qwerty") =
        YggmailConfig("127.0.0.1", port, "127.0.0.1", 1143, login, password)

    @Test fun smtpUsesAuthPlainAndNeverAuthLogin() {
        val smtp = server("LOGIN PLAIN")
        val result = runBlocking { JavaMailYggmailMail().send(config(smtp.port), "abc@yggmail", byteArrayOf(1, 2, 3)) }
        assertTrue("отправка должна пройти: ${result.exceptionOrNull()}", result.isSuccess)
        assertFalse("AUTH LOGIN отправлять нельзя: ${smtp.commands}", smtp.commands.any { it.startsWith("AUTH LOGIN") })
        assertTrue(smtp.commands.any { it.startsWith("AUTH PLAIN") })
    }

    @Test fun plainCredentialsAreNulLoginNulPasswordWithEmptyAuthzid() {
        val smtp = server("LOGIN PLAIN")
        runBlocking { JavaMailYggmailMail().send(config(smtp.port, login = "login@yggmail", password = "qwerty-123"), "abc@yggmail", byteArrayOf(1)) }
        assertEquals("\u0000login@yggmail\u0000qwerty-123", smtp.plainCredentials)
    }

    @Test fun letterHasExactSubjectAndBase64Body() {
        val smtp = server("LOGIN PLAIN")
        runBlocking { JavaMailYggmailMail().send(config(smtp.port), "abc@yggmail", byteArrayOf(1, 2, 3)) }
        assertTrue(smtp.data.lines().any { it == "Subject: Ramka-E2EE" })
        assertTrue(smtp.data.lines().any { it.trim() == "AQID" }) // Base64 от байтов 1,2,3
        assertTrue(smtp.commands.any { it.startsWith("RCPT TO") && it.contains("abc@yggmail") })
    }

    @Test fun serverWithOnlyLoginIsRefusedWithoutSendingAuthLogin() {
        val smtp = server("LOGIN")
        val result = runBlocking { JavaMailYggmailMail().send(config(smtp.port), "abc@yggmail", byteArrayOf(1)) }
        assertTrue(result.isFailure)
        assertFalse(smtp.commands.any { it.startsWith("AUTH LOGIN") })
    }

    @Test fun testButtonPathAlsoAvoidsAuthLogin() {
        val smtp = server("LOGIN PLAIN")
        // «Проверить»: SMTP проходит, IMAP на порту 1143 недоступен — важно только, что до этого AUTH LOGIN не было.
        runBlocking { JavaMailYggmailMail().test(config(smtp.port)) }
        assertFalse(smtp.commands.any { it.startsWith("AUTH LOGIN") })
        assertTrue(smtp.commands.any { it.startsWith("AUTH PLAIN") })
    }

    @Test fun sessionPropertiesRestrictSmtpToPlain() {
        val props = JavaMailYggmailMail().sessionProperties(config(1025), loginCommandOnly = false)
        for (protocol in listOf("smtp", "smtps")) {
            assertEquals("PLAIN", props["mail.$protocol.auth.mechanisms"])
            assertEquals("", props["mail.$protocol.sasl.authorizationid"])
        }
        assertEquals("false", props["mail.debug.auth"])
    }

    @Test fun imapStillPrefersPlainLoginCommand() {
        val props = JavaMailYggmailMail().sessionProperties(config(1025), loginCommandOnly = true)
        assertEquals("true", props["mail.imap.auth.plain.disable"])
        assertEquals("true", props["mail.imap.auth.login.disable"])
    }

    /** Минимальный SMTP: приветствие, EHLO с AUTH, AUTH PLAIN (сразу или по 334), MAIL/RCPT/DATA/QUIT. */
    private class FakeSmtpServer(private val mechanisms: String) : Closeable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val commands = CopyOnWriteArrayList<String>()
        @Volatile var plainCredentials: String? = null
        @Volatile var data: String = ""

        init {
            thread(isDaemon = true) { runCatching { serve() } }
        }

        private fun serve() {
            socket.accept().use { client ->
                client.soTimeout = 10_000
                val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val out = client.getOutputStream()
                fun reply(text: String) {
                    out.write((text + "\r\n").toByteArray(Charsets.ISO_8859_1))
                    out.flush()
                }
                fun savePlain(b64: String) {
                    plainCredentials = String(Base64.getDecoder().decode(b64.trim()), Charsets.UTF_8)
                }
                reply("220 fake ESMTP")
                while (true) {
                    val line = input.readLine() ?: break
                    // Токен с учётными данными в список команд не кладём.
                    commands += if (line.startsWith("AUTH PLAIN ")) "AUTH PLAIN <initial-response>" else line
                    when {
                        line.startsWith("EHLO") || line.startsWith("HELO") -> {
                            reply("250-fake")
                            reply("250-AUTH $mechanisms")
                            reply("250 8BITMIME")
                        }
                        line.startsWith("AUTH LOGIN") -> { // как паника Yggmail: соединение обрывается
                            reply("451 4.0.0 internal error")
                            break
                        }
                        line == "AUTH PLAIN" -> {
                            reply("334 ")
                            savePlain(input.readLine() ?: break)
                            reply("235 2.0.0 Authentication succeeded")
                        }
                        line.startsWith("AUTH PLAIN ") -> {
                            savePlain(line.removePrefix("AUTH PLAIN "))
                            reply("235 2.0.0 Authentication succeeded")
                        }
                        line.startsWith("MAIL FROM") -> reply("250 OK")
                        line.startsWith("RCPT TO") -> reply("250 OK")
                        line == "DATA" -> {
                            reply("354 go ahead")
                            val body = StringBuilder()
                            while (true) {
                                val dataLine = input.readLine() ?: break
                                if (dataLine == ".") break
                                body.append(dataLine).append('\n')
                            }
                            data = body.toString()
                            reply("250 2.0.0 queued")
                        }
                        line == "QUIT" -> {
                            reply("221 bye")
                            break
                        }
                        else -> reply("250 OK")
                    }
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }
}
