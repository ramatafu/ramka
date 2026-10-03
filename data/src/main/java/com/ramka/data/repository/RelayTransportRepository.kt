package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayProvider
import com.ramka.domain.repository.TransportRepository
import com.ramka.network.local.TunnelMessageChannel
import com.ramka.network.relay.RelayConnectResult
import com.ramka.network.relay.RelayConnectionState
import com.ramka.network.relay.RelayTunnelClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * Транспорт через домашний релей (этап 3, RELAY_PROTOCOL.md).
 *
 * Релей — «слепая труба»: он сводит двух онлайн-клиентов и перекладывает байты, а всё остальное
 * (IK-рукопожатие, проверка подписей, шифрование, выравнивание) выполняет тот же [TunnelMessageChannel],
 * что и в LAN. Релей не может прочитать или подделать сообщение.
 *
 * Какие релеи использовать, решает [RelayProvider] (сейчас один из настроек; список с
 * переключением добавляется новой реализацией провайдера, этот класс не меняется).
 */
class RelayTransportRepository(
    private val tunnels: TunnelMessageChannel,
    private val client: RelayTunnelClient,
    private val provider: RelayProvider
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
        for (endpoint in provider.relaysFor(contact)) {
            when (val result = client.connectTo(endpoint, contact.signingPublicKey)) {
                is RelayConnectResult.Connected -> {
                    provider.report(endpoint, null)
                    // Рукопожатие и отправка — как в LAN; итог (в т.ч. «подпись собеседника не сошлась») окончателен.
                    return sendOverTunnel(result.socket, contact, packet)
                }
                is RelayConnectResult.Failed -> {
                    // «Получателя нет в сети» — это не поломка релея; со списком релеев пробуем следующий.
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
     * Входящие через релей: пока релей включён и настроен, держится регистрация, на каждую «трубу»
     * проводится рукопожатие в отдельной корутине (медленный отправитель не задерживает остальных).
     * Смена или выключение релея в настройках пересоздаёт подписку.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> =
        provider.ownRelays.flatMapLatest { endpoints -> receiveFrom(endpoints) }

    private fun receiveFrom(endpoints: List<RelayEndpoint>): Flow<Pair<ByteArray, ByteArray>> = channelFlow {
        for (endpoint in endpoints) {
            launch {
                client.incoming(endpoint) { state -> reportState(endpoint, state) }.collect { tunnel ->
                    launch(Dispatchers.IO) {
                        val message = runCatching { tunnels.processIncomingSocket(tunnel) }.getOrNull()
                        if (message != null) send(message.senderStaticX25519 to message.plaintext)
                    }
                }
            }
        }
    }

    private fun reportState(endpoint: RelayEndpoint, state: RelayConnectionState) {
        when (state) {
            RelayConnectionState.Connecting -> Unit
            RelayConnectionState.Registered -> provider.report(endpoint, null)
            is RelayConnectionState.Failed -> provider.report(endpoint, state.failure)
        }
    }
}
