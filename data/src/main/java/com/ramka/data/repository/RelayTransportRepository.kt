package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.relay.RelayClock
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayLinkState
import com.ramka.domain.relay.RelayProvider
import com.ramka.domain.repository.TransportRepository
import com.ramka.network.local.TunnelMessageChannel
import com.ramka.network.relay.RelayConnectResult
import com.ramka.network.relay.RelayConnectionState
import com.ramka.network.relay.RelayTunnelClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * Бюджет времени отправки через релеи. Раньше каждый релей получал полный дедлайн (15 с), и при трёх
 * мёртвых релеях сообщение висело в «отправляется» почти минуту.
 *
 * @property totalBudgetMillis сколько всего ищем рабочий релей (резолв, TLS, CONNECT → READY на всех
 *   релеях вместе). Рукопожатие после установки «трубы» ограничено собственными таймаутами канала.
 * @property firstAttemptMillis потолок для первого релея в очереди.
 * @property fallbackAttemptMillis потолок для каждого следующего (запасного) релея: он короче, чтобы
 *   мёртвый релей не съедал весь бюджет.
 * @property minAttemptMillis если на попытку осталось меньше, её не начинаем.
 */
data class RelaySendConfig(
    val totalBudgetMillis: Long = 25_000,
    val firstAttemptMillis: Long = 15_000,
    val fallbackAttemptMillis: Long = 8_000,
    val minAttemptMillis: Long = 1_000
)

/**
 * Транспорт через домашние релеи (этап 3, RELAY_PROTOCOL.md).
 *
 * Релей — «слепая труба»: он сводит двух онлайн-клиентов и перекладывает байты, а всё остальное
 * (IK-рукопожатие, проверка подписей, шифрование, выравнивание) выполняет тот же [TunnelMessageChannel],
 * что и в LAN. Релей не может прочитать или подделать сообщение.
 *
 * Какие релеи и в каком порядке пробовать, решает [RelayProvider] ([com.ramka.domain.relay.PoolRelayProvider]
 * учитывает приоритеты, паузы после ошибок и «залипание»); этот класс только идёт по списку в рамках бюджета
 * времени и сообщает провайдеру итоги.
 */
class RelayTransportRepository(
    private val tunnels: TunnelMessageChannel,
    private val client: RelayTunnelClient,
    private val provider: RelayProvider,
    private val clock: RelayClock = RelayClock.System,
    private val sendConfig: RelaySendConfig = RelaySendConfig()
) : TransportRepository {

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        // Контракт TransportRepository: отправка НЕ бросает исключений, `false` = «не вышло, сообщение
        // останется в Outbox». Вызывающие (SendMessageUseCase в viewModelScope, DelayedAckSender в
        // appScope) исключений не ждут, а любое необработанное исключение там завершает процесс.
        return try {
            sendViaRelays(contact, packet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun sendViaRelays(contact: Contact, packet: ByteArray): Boolean {
        val started = clock.nowMillis()
        for ((index, endpoint) in provider.relaysFor(contact).withIndex()) {
            val remaining = sendConfig.totalBudgetMillis - (clock.nowMillis() - started)
            if (remaining < sendConfig.minAttemptMillis) break // бюджет исчерпан: Outbox повторит позже
            val limit = if (index == 0) sendConfig.firstAttemptMillis else sendConfig.fallbackAttemptMillis
            val attemptStarted = clock.nowMillis()

            when (val result = client.connectTo(endpoint, contact.signingPublicKey, minOf(limit, remaining))) {
                is RelayConnectResult.Connected -> {
                    provider.report(endpoint, null)
                    // Рукопожатие и отправка — как в LAN; итог (в т.ч. «подпись собеседника не сошлась») окончателен.
                    val delivered = sendOverTunnel(result.socket, contact, packet)
                    if (delivered) {
                        provider.reportDelivery(endpoint, contact, clock.nowMillis() - attemptStarted, fallback = index > 0)
                    }
                    return delivered
                }
                is RelayConnectResult.Failed -> {
                    // «Получателя нет в сети» — это не поломка релея; пробуем следующий (он мог быть
                    // тем, где собеседник зарегистрирован).
                    provider.report(endpoint, result.failure.takeUnless { it == RelayFailure.TARGET_OFFLINE })
                }
            }
        }
        return false
    }

    /**
     * Отправка по «трубе» и закрытие сокета. Закрытие НЕ может менять результат и бросать исключение:
     * к этому моменту релей и получатель уже могли закрыть соединение, и `close()` TLS-сокета
     * (отправка close_notify в закрытое соединение) способен бросить IOException — именно в момент
     * успешной доставки. С `socket.use { }` такое исключение вылетало наружу, мимо результата `true`.
     */
    private suspend fun sendOverTunnel(socket: Socket, contact: Contact, packet: ByteArray): Boolean =
        try {
            tunnels.sendMessageOver(socket, contact.publicKey, contact.signingPublicKey, packet)
        } finally {
            closeQuietly(socket)
        }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Входящие через релеи: регистрация на каждом релее из [RelayProvider.ownRelays], на каждую «трубу»
     * рукопожатие в отдельной корутине (медленный отправитель не задерживает остальных).
     *
     * Набор релеёв сравнивается как МНОЖЕСТВО: перестановка списка не трогает рабочие соединения, смена
     * одного релея (адрес, токен, пин) перезапускает только его, выключение пула отменяет всё.
     */
    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = channelFlow {
        val registrations = HashMap<RelayEndpoint, Job>()
        provider.ownRelays.collect { endpoints ->
            val wanted = endpoints.toSet()
            for (gone in registrations.keys.filter { it !in wanted }) registrations.remove(gone)?.cancel()
            for (endpoint in wanted) {
                if (endpoint !in registrations) registrations[endpoint] = launch { register(endpoint, this@channelFlow) }
            }
        }
    }

    /** Одна регистрация на одном релее; живёт, пока её не отменят (смена набора, выключение, остановка приёма). */
    private suspend fun register(endpoint: RelayEndpoint, out: SendChannel<Pair<ByteArray, ByteArray>>) = coroutineScope {
        provider.reportLink(endpoint, RelayLinkState.Connecting)
        client.incoming(endpoint) { state -> provider.reportLink(endpoint, state.toLinkState()) }.collect { tunnel ->
            launch(Dispatchers.IO) {
                val message = runCatching { tunnels.processIncomingSocket(tunnel) }.getOrNull()
                if (message != null) out.send(message.senderStaticX25519 to message.plaintext)
            }
        }
    }

    private fun RelayConnectionState.toLinkState(): RelayLinkState = when (this) {
        RelayConnectionState.Connecting -> RelayLinkState.Connecting
        RelayConnectionState.Registered -> RelayLinkState.Connected
        is RelayConnectionState.Failed -> RelayLinkState.Failed(failure, retryInMillis)
    }
}
