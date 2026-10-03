package com.ramka.network.relay

import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.util.Jitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Долговременная подпись устройства (Ed25519) для REGISTER (RELAY_PROTOCOL.md §3, §4.1). */
interface RelayIdentity {
    val ed25519Public: ByteArray
    fun sign(message: ByteArray): ByteArray
}

sealed interface RelayConnectResult {
    /** Труба до получателя готова (получен READY); сокетом владеет вызывающий и обязан его закрыть. */
    class Connected(val socket: Socket) : RelayConnectResult

    data class Failed(val failure: RelayFailure) : RelayConnectResult
}

/** Состояние регистрации на релее — для статуса в UI и [com.ramka.domain.relay.RelayProvider.report]. */
sealed interface RelayConnectionState {
    object Connecting : RelayConnectionState
    object Registered : RelayConnectionState
    data class Failed(val failure: RelayFailure, val retryInMillis: Long) : RelayConnectionState
}

/** Клиентская часть протокола релея; интерфейс нужен транспорту и тестам. */
interface RelayTunnelClient {
    /** Отправка: CONNECT к получателю с данным Ed25519-ключом; при успехе возвращает «трубу» (§4.2). */
    suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray): RelayConnectResult

    /**
     * Приём: держит REGISTER на релее (с переподключением) и эмитит по одной «трубе» на каждое входящее
     * соединение. Владелец полученного сокета — подписчик. Отмена подписки закрывает всё.
     */
    fun incoming(endpoint: RelayEndpoint, onState: (RelayConnectionState) -> Unit = {}): Flow<Socket>
}

/** Таймауты и лимиты клиента; значения по умолчанию — из RELAY_PROTOCOL.md §4. */
data class RelayClientConfig(
    val connectTimeoutMillis: Int = 10_000,
    /** Общий дедлайн CONNECT → READY, включая резолв и TLS (§4.2). */
    val connectDeadlineMillis: Long = 15_000,
    /** Ожидание ответов на REGISTER (CHALLENGE, REGISTERED) и на check. */
    val handshakeIoTimeoutMillis: Int = 10_000,
    /** Ожидание READY после ACCEPT. */
    val acceptReadyTimeoutMillis: Int = 15_000,
    val pingMinMillis: Long = 20_000, // §4.1: PING каждые 20–40 с
    val pingMaxMillis: Long = 40_000,
    val pongTimeoutMillis: Long = 15_000, // §4.1
    val maxConcurrentAccepts: Int = 4, // §8
    val backoffBaseMillis: Long = 1_000, // §4.1: 1 с → 60 с
    val backoffCapMillis: Long = 60_000,
    /** Пауза после ERR(REPLACED), чтобы два устройства с одним ключом не вытесняли друг друга по кругу. */
    val replacedMinDelayMillis: Long = 30_000,
    /** Пауза после ошибки, которую повтор не исправит без смены настроек (неверный токен, нет TLS 1.3). */
    val fatalRetryMillis: Long = 5 * 60_000
)

/**
 * Клиент релея (RELAY_PROTOCOL.md v1): REGISTER/PING для приёма, CONNECT/ACCEPT для доставки,
 * check для проверки настроек. Не знает про IK-рукопожатие и сообщения: после READY отдаёт
 * «трубу», по которой идёт обычный формат ramka.
 *
 * Блокирующий ввод-вывод выполняется на Dispatchers.IO; отмена корутины закрывает сокет,
 * поэтому блокирующее чтение прерывается.
 */
class RelayClient(
    private val identity: RelayIdentity,
    private val connector: RelayConnector,
    private val jitter: Jitter = Jitter(),
    private val config: RelayClientConfig = RelayClientConfig(),
    private val random: Random = Random.Default
) : RelayTunnelClient, RelayChecker {

    // ---- Отправка (§4.2) ----------------------------------------------------------------

    override suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray): RelayConnectResult =
        withContext(Dispatchers.IO) {
            val target = try {
                RelayProtocol.routingId(targetSigningPublicKey)
            } catch (e: IllegalArgumentException) {
                return@withContext RelayConnectResult.Failed(RelayFailure.PROTOCOL)
            }
            val holder = SocketHolder()
            try {
                guarded<RelayConnectResult>(holder) {
                    val deadline = System.nanoTime() + config.connectDeadlineMillis * 1_000_000L
                    val socket = connector.connect(endpoint, config.connectTimeoutMillis)
                    holder.socket = socket
                    RelayProtocol.writeFrame(
                        socket.getOutputStream(), RelayProtocol.CONNECT,
                        RelayProtocol.authBody(endpoint.token, target)
                    )
                    val frame = readFrameBefore(socket, socket.getInputStream(), deadline)
                    if (frame.type != RelayProtocol.READY || frame.body.isNotEmpty()) throw RelayProtocol.unexpected(frame)
                    socket.soTimeout = 0 // дальше таймауты задаёт SecureLanChannel
                    holder.keep = true
                    RelayConnectResult.Connected(socket)
                }
            } catch (e: RelayException) {
                RelayConnectResult.Failed(e.failure)
            } catch (e: SocketTimeoutException) {
                RelayConnectResult.Failed(RelayFailure.TIMEOUT)
            } catch (e: IOException) {
                RelayConnectResult.Failed(RelayFailure.UNREACHABLE)
            } catch (e: IllegalArgumentException) {
                RelayConnectResult.Failed(RelayFailure.PROTOCOL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RelayConnectResult.Failed(RelayFailure.INTERNAL)
            }
        }

    // ---- Проверка настроек --------------------------------------------------------------

    /**
     * Проверяет TLS (пин/CA), токен и протокол: CONNECT к случайному несуществующему routing_id.
     * Ответ ERR(OFFLINE) означает, что релей принял токен, то есть всё в порядке. Регистрация
     * этого устройства не затрагивается (в отличие от REGISTER, который вытеснил бы рабочее соединение).
     */
    override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult = withContext(Dispatchers.IO) {
        val holder = SocketHolder()
        try {
            guarded<RelayCheckResult>(holder) {
                val socket = connector.connect(endpoint, config.connectTimeoutMillis)
                holder.socket = socket
                val target = ByteArray(RelayProtocol.ROUTING_ID_SIZE).also { random.nextBytes(it) }
                RelayProtocol.writeFrame(
                    socket.getOutputStream(), RelayProtocol.CONNECT,
                    RelayProtocol.authBody(endpoint.token, target)
                )
                socket.soTimeout = config.handshakeIoTimeoutMillis
                val frame = RelayProtocol.readFrame(socket.getInputStream())
                val offline = frame.type == RelayProtocol.ERR && frame.body.size == 1 &&
                    (frame.body[0].toInt() and 0xFF) == RelayProtocol.CODE_OFFLINE
                if (offline || frame.type == RelayProtocol.READY) RelayCheckResult.Ok else throw RelayProtocol.unexpected(frame)
            }
        } catch (e: RelayException) {
            RelayCheckResult.Failed(e.failure)
        } catch (e: SocketTimeoutException) {
            RelayCheckResult.Failed(RelayFailure.TIMEOUT)
        } catch (e: IOException) {
            RelayCheckResult.Failed(RelayFailure.UNREACHABLE)
        } catch (e: IllegalArgumentException) {
            RelayCheckResult.Failed(RelayFailure.PROTOCOL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RelayCheckResult.Failed(RelayFailure.INTERNAL)
        }
    }

    // ---- Приём (§4.1, §4.2) -------------------------------------------------------------

    override fun incoming(endpoint: RelayEndpoint, onState: (RelayConnectionState) -> Unit): Flow<Socket> = channelFlow {
        val attempt = AtomicInteger(0)
        while (isActive) {
            onState(RelayConnectionState.Connecting)
            val failure = runRegistration(endpoint, this) {
                attempt.set(0)
                onState(RelayConnectionState.Registered)
            }
            val retryIn = nextDelayMillis(failure, attempt.getAndIncrement())
            onState(RelayConnectionState.Failed(failure, retryIn))
            delay(retryIn)
        }
    }

    /** Одна сессия регистрации; всегда завершается причиной обрыва (нормального конца у неё нет). */
    private suspend fun runRegistration(
        endpoint: RelayEndpoint,
        out: SendChannel<Socket>,
        onRegistered: () -> Unit
    ): RelayFailure = withContext(Dispatchers.IO) {
        val holder = SocketHolder()
        try {
            guarded<RelayFailure>(holder) {
                val socket = connector.connect(endpoint, config.connectTimeoutMillis)
                holder.socket = socket
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                socket.soTimeout = config.handshakeIoTimeoutMillis

                val publicKey = identity.ed25519Public
                val routingId = RelayProtocol.routingId(publicKey)
                RelayProtocol.writeFrame(
                    output, RelayProtocol.REGISTER,
                    RelayProtocol.authBody(endpoint.token, routingId + publicKey)
                )
                val challenge = RelayProtocol.readFrame(input)
                if (challenge.type != RelayProtocol.CHALLENGE || challenge.body.size != RelayProtocol.NONCE_SIZE) {
                    throw RelayProtocol.unexpected(challenge)
                }
                val signature = identity.sign(RelayProtocol.proofMessage(challenge.body, routingId))
                RelayProtocol.writeFrame(output, RelayProtocol.PROOF, signature)
                val registered = RelayProtocol.readFrame(input)
                if (registered.type != RelayProtocol.REGISTERED) throw RelayProtocol.unexpected(registered)

                onRegistered()
                socket.soTimeout = 0 // живость проверяет PING/PONG, а не таймаут чтения
                serve(endpoint, socket, input, output, out)
                RelayFailure.UNREACHABLE // serve выходит только исключением
            }
        } catch (e: RelayException) {
            e.failure
        } catch (e: SocketTimeoutException) {
            RelayFailure.TIMEOUT
        } catch (e: IOException) {
            RelayFailure.UNREACHABLE
        } catch (e: IllegalArgumentException) {
            RelayFailure.PROTOCOL
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RelayFailure.INTERNAL // неожиданная ошибка не должна обрывать цикл регистрации (и тем более приложение)
        }
    }

    /** Основной цикл управляющего соединения: PING/PONG и обработка INCOMING. Выходит только исключением. */
    private suspend fun serve(
        endpoint: RelayEndpoint,
        socket: Socket,
        input: InputStream,
        output: java.io.OutputStream,
        out: SendChannel<Socket>
    ) = coroutineScope {
        val pongs = Channel<Unit>(Channel.CONFLATED)
        val accepts = Semaphore(config.maxConcurrentAccepts)
        val pongTimedOut = AtomicBoolean(false)

        // §4.1: PING каждые 20–40 с (случайно); нет PONG за 15 с — соединение мёртвое.
        val pinger = launch {
            while (true) {
                delay(random.nextLong(config.pingMinMillis, config.pingMaxMillis + 1))
                RelayProtocol.writeFrame(output, RelayProtocol.PING)
                if (withTimeoutOrNull(config.pongTimeoutMillis) { pongs.receive() } == null) {
                    pongTimedOut.set(true)
                    closeQuietly(socket) // прерывает блокирующее чтение ниже
                    return@launch
                }
            }
        }
        try {
            while (true) {
                val frame = RelayProtocol.readFrame(input)
                when (frame.type) {
                    RelayProtocol.PONG -> pongs.trySend(Unit)
                    RelayProtocol.INCOMING -> {
                        if (frame.body.size != RelayProtocol.SESSION_ID_SIZE) {
                            throw RelayException(RelayFailure.PROTOCOL, "INCOMING неверной длины")
                        }
                        val sessionId = frame.body
                        // Сверх лимита одновременных ACCEPT — пропускаем: у отправителя истечёт тайм-аут,
                        // сообщение останется в его Outbox и придёт при следующей попытке.
                        if (accepts.tryAcquire()) {
                            launch {
                                try {
                                    val tunnel = acceptOne(endpoint, sessionId)
                                    if (tunnel != null) deliver(out, tunnel)
                                } finally {
                                    accepts.release()
                                }
                            }
                        }
                    }
                    else -> throw RelayProtocol.unexpected(frame) // ERR (в т.ч. REPLACED) или мусор
                }
            }
        } catch (e: IOException) {
            if (pongTimedOut.get()) throw RelayException(RelayFailure.TIMEOUT, "Нет PONG", e)
            throw e
        } finally {
            pinger.cancel()
        }
    }

    /** Открывает отдельное соединение с ACCEPT и ждёт READY; null — не вышло (отправитель повторит). */
    private suspend fun acceptOne(endpoint: RelayEndpoint, sessionId: ByteArray): Socket? =
        withContext(Dispatchers.IO) {
            val holder = SocketHolder()
            try {
                guarded<Socket>(holder) {
                    val socket = connector.connect(endpoint, config.connectTimeoutMillis)
                    holder.socket = socket
                    RelayProtocol.writeFrame(
                        socket.getOutputStream(), RelayProtocol.ACCEPT,
                        RelayProtocol.authBody(endpoint.token, sessionId)
                    )
                    socket.soTimeout = config.acceptReadyTimeoutMillis
                    val frame = RelayProtocol.readFrame(socket.getInputStream())
                    if (frame.type != RelayProtocol.READY || frame.body.isNotEmpty()) throw RelayProtocol.unexpected(frame)
                    socket.soTimeout = 0
                    holder.keep = true
                    socket
                }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun deliver(out: SendChannel<Socket>, tunnel: Socket) {
        try {
            out.send(tunnel)
        } catch (e: CancellationException) {
            closeQuietly(tunnel)
            throw e
        }
    }

    /** Пауза перед повтором регистрации: backoff 1 с → 60 с с джиттером ±20% (этап 2.5, [Jitter]). */
    internal fun nextDelayMillis(failure: RelayFailure, attempt: Int): Long {
        val base = when (failure) {
            RelayFailure.AUTH, RelayFailure.TLS13_UNSUPPORTED -> config.fatalRetryMillis
            else -> (config.backoffBaseMillis shl attempt.coerceIn(0, 10)).coerceAtMost(config.backoffCapMillis)
        }
        val floor = if (failure == RelayFailure.REPLACED) config.replacedMinDelayMillis else 0L
        return maxOf(jitter.applyBackoff(base), floor)
    }

    // ---- Служебное ----------------------------------------------------------------------

    private fun readFrameBefore(socket: Socket, input: InputStream, deadlineNanos: Long): RelayProtocol.Frame {
        val remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000L
        if (remainingMillis <= 0) throw SocketTimeoutException("Дедлайн CONNECT")
        socket.soTimeout = remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return RelayProtocol.readFrame(input)
    }

    /** Сокет операции: закрывается при выходе из [guarded], если не помечен [keep]. */
    private class SocketHolder {
        @Volatile var socket: Socket? = null
        @Volatile var keep: Boolean = false
        fun closeUnlessKept() {
            if (!keep) socket?.let { closeQuietly(it) }
        }
    }

    /**
     * Выполняет [block] и гарантирует закрытие сокета: при ошибке, при отмене корутины (это
     * прерывает блокирующее чтение) и при нормальном завершении, если [SocketHolder.keep] не выставлен.
     */
    private suspend fun <T> guarded(holder: SocketHolder, block: suspend () -> T): T = coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                holder.closeUnlessKept()
            }
        }
        try {
            block()
        } finally {
            watcher.cancel()
            holder.closeUnlessKept()
        }
    }
}

private fun closeQuietly(socket: Socket) {
    try {
        socket.close()
    } catch (_: IOException) {
    }
}
