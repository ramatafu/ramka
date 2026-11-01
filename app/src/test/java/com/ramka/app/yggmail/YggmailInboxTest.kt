package com.ramka.app.yggmail

import com.ramka.domain.model.Contact
import com.ramka.domain.util.OfflinePacketSealer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailInboxTest {
    private class FakeMail(val bodies: List<String>) : YggmailMail {
        val handled = mutableListOf<Boolean>()
        var receiveCalls = 0
        override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> = Result.success(Unit)
        override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> {
            receiveCalls++
            bodies.forEach { handled += handler(it) }
            return Result.success(handled.count { it })
        }
        override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
    }

    /** ТЕСТОВАЯ «схема»: sealed = [ключ отправителя 1 байт][текст]. Не шифр — только проверка маршрута. */
    private class FakeSealer(override val isAvailable: Boolean = true) : OfflinePacketSealer {
        override suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray? = null
        override suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>? =
            if (sealed.size < 2 || sealed[0] != 7.toByte()) null else byteArrayOf(sealed[0]) to sealed.copyOfRange(1, sealed.size)
    }

    private val config = YggmailConfig("200:db8::1", 1025, "200:db8::1", 1143, "me@yggmail", "pw")
    private val source = object : YggmailConfigSource { override fun load(): YggmailConfig? = config }
    private val noConfig = object : YggmailConfigSource { override fun load(): YggmailConfig? = null }

    private fun body(vararg bytes: Int) = YggmailPayload.encode(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun inbox(mail: FakeMail, sealer: OfflinePacketSealer = FakeSealer(), enabled: Boolean = true, cfg: YggmailConfigSource = source) =
        YggmailInbox(cfg, mail, sealer, { enabled }, subscriberWaitMs = 0L)

    @Test fun validLetterIsOpenedEmittedAndAcknowledged() = runBlocking {
        val mail = FakeMail(listOf(body(7, 1, 2, 3)))
        val inbox = inbox(mail)
        val received = mutableListOf<Pair<ByteArray, ByteArray>>()
        val job = launch(Dispatchers.Unconfined) { inbox.packets.collect { received += it } }
        val result = inbox.pollOnce()
        job.cancel()
        assertEquals(1, result.getOrNull())
        assertEquals(listOf(true), mail.handled)
        assertEquals(1, received.size)
        assertArrayEquals(byteArrayOf(7), received[0].first)
        assertArrayEquals(byteArrayOf(1, 2, 3), received[0].second)
    }

    @Test fun garbageIsDiscardedButMarkedRead() = runBlocking {
        val mail = FakeMail(listOf("это не base64!", body(9, 9, 9), body(7)))
        val inbox = inbox(mail)
        val received = mutableListOf<Pair<ByteArray, ByteArray>>()
        val job = launch(Dispatchers.Unconfined) { inbox.packets.collect { received += it } }
        inbox.pollOnce()
        job.cancel()
        assertEquals(listOf(true, true, true), mail.handled) // все помечены прочитанными, повторно не разбираются
        assertTrue(received.isEmpty())                       // ничего не дошло до обработчика входящих
    }

    @Test fun withoutSubscriberLettersStayUnread() = runBlocking {
        val mail = FakeMail(listOf(body(7, 1)))
        val result = inbox(mail).pollOnce()
        assertEquals(0, result.getOrNull())
        assertEquals(0, mail.receiveCalls) // даже не ходили на сервер
    }

    @Test fun disabledOrUnavailableOrUnconfiguredDoesNothing() = runBlocking {
        val mail = FakeMail(listOf(body(7, 1)))
        assertEquals(0, inbox(mail, enabled = false).pollOnce().getOrNull())
        assertEquals(0, inbox(mail, sealer = FakeSealer(isAvailable = false)).pollOnce().getOrNull())
        assertEquals(0, inbox(mail, cfg = noConfig).pollOnce().getOrNull())
        assertEquals(0, mail.receiveCalls)
    }

    @Test fun mailFailureBecomesFailureResult() = runBlocking {
        val failing = object : YggmailMail {
            override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> = Result.success(Unit)
            override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> = Result.failure(java.net.ConnectException("down"))
            override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
        }
        val inbox = YggmailInbox(source, failing, FakeSealer(), { true }, subscriberWaitMs = 0L)
        val job = launch(Dispatchers.Unconfined) { inbox.packets.collect { } }
        assertTrue(inbox.pollOnce().isFailure)
        job.cancel()
    }

    @Test fun throwingMailClientIsContained() = runBlocking {
        val boom = object : YggmailMail {
            override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> = Result.success(Unit)
            override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> = throw IllegalStateException("boom")
            override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
        }
        val inbox = YggmailInbox(source, boom, FakeSealer(), { true }, subscriberWaitMs = 0L)
        val job = launch(Dispatchers.Unconfined) { inbox.packets.collect { } }
        assertTrue(inbox.pollOnce().isFailure)
        job.cancel()
    }
}
