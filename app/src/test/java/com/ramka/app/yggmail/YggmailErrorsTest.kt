package com.ramka.app.yggmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.net.ssl.SSLHandshakeException

class YggmailErrorsTest {
    @Test fun mapsKnownErrors() {
        assertEquals("Сервер отклонил вход: x", YggmailErrors.describe(AuthenticationFailedException("x")))
        assertEquals("Адрес сервера не найден", YggmailErrors.describe(UnknownHostException("x")))
        assertEquals("Сервер не отвечает (таймаут)", YggmailErrors.describe(SocketTimeoutException("x")))
        assertTrue(YggmailErrors.describe(ConnectException("x")).startsWith("Не удалось подключиться"))
        assertTrue(YggmailErrors.describe(SSLHandshakeException("x")).startsWith("Ошибка TLS"))
    }

    @Test fun authFailureShowsServerReplyInsteadOfGuessingBadPassword() {
        val text = YggmailErrors.describe(AuthenticationFailedException("[AUTHENTICATIONFAILED] Invalid credentials"))
        assertTrue(text.contains("Invalid credentials"))
        assertTrue(!text.contains("Неверный пароль"))
    }

    @Test fun authFailureWithoutMessageStillReadable() {
        assertEquals("Сервер отклонил вход", YggmailErrors.describe(AuthenticationFailedException()))
    }

    @Test fun stageIsReportedSoItIsClearWhichServerFailed() {
        val smtp = YggmailStageException("SMTP", AuthenticationFailedException("535 5.7.8 bad credentials"))
        assertEquals("SMTP: Сервер отклонил вход: 535 5.7.8 bad credentials", YggmailErrors.describe(smtp))
        val imap = YggmailStageException("IMAP", ConnectException("refused"))
        assertTrue(YggmailErrors.describe(imap).startsWith("IMAP: Не удалось подключиться"))
    }

    @Test fun serverTextIsTruncated() {
        val text = YggmailErrors.describe(AuthenticationFailedException("я".repeat(1000)))
        assertTrue(text.length < 200)
    }

    @Test fun unwrapsMessagingException() {
        val wrapped = MessagingException("outer", UnknownHostException("inner"))
        assertEquals("Адрес сервера не найден", YggmailErrors.describe(wrapped))
    }

    @Test fun unknownErrorShowsOnlyClassName() {
        val text = YggmailErrors.describe(IllegalArgumentException("secret-host-and-password"))
        assertTrue(text.contains("IllegalArgumentException"))
        assertTrue(!text.contains("secret"))
    }
}
