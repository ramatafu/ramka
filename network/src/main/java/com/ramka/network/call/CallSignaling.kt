package com.ramka.network.call

import com.ramka.domain.model.AppMessage
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Сигналинг звонков (этап 7.3): обмен SDP и ICE между двумя контактами через уже существующий
 * [TransportRepository] — своего сетевого слоя нет. Каждый сигнал — обычный [AppMessage] (`CallOffer`,
 * `CallAnswer`, `CallIceCandidate`, `CallEnd`) в том же зашифрованном канале, что и сообщения чата:
 * полное IK-рукопожатие, выравнивание кадров и проверка отправителя по списку контактов — как раньше.
 *
 * ## Приём
 * Подписываться на `TransportRepository.incomingPackets()` второй раз нельзя: каждая подписка открывает
 * свой серверный сокет. Поэтому единственный подписчик — `IncomingMessageProcessor` — передаёт сюда
 * сигналы звонков через [onIncoming], а потребители читают [incoming]. Отправитель уже
 * аутентифицирован транспортом, [Signal.contactId] — localId контакта по его публичному ключу.
 * У [incoming] нет повтора (replay): сигнал, пришедший, когда никто не слушает, теряется — для звонка
 * это правильно (устаревший offer принимать нельзя; звонящий сам прекратит попытку по таймауту).
 *
 * ## Отправка
 * Сигналы уходят строго по очереди (FIFO) одним рабочим циклом: offer всегда раньше своих ICE-кандидатов,
 * иначе получатель получил бы кандидаты к ещё не существующему звонку. Каждое сообщение — отдельное
 * соединение с рукопожатием (так устроен транспорт), поэтому задержка на сигнал порядка сотен миллисекунд;
 * для звонка в локальной сети это приемлемо. Джиттера, как у ACK (этап 2.5), здесь нет: он нужен для
 * маскировки паттернов сообщений, а для звонка добавил бы секунды к установке соединения.
 * Outbox не используется: протухший звонок нельзя «доставить позже».
 *
 * @param scope долгоживущий scope приложения: рабочий цикл должен пережить экран/ViewModel, иначе
 *   последний `CallEnd` при закрытии экрана не успел бы уйти.
 * @param retryDelayMillis пауза между повторными попытками одного сигнала.
 */
class CallSignaling(
    private val contactRepository: ContactRepository,
    private val transport: TransportRepository,
    scope: CoroutineScope,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MS
) {

    /** Входящий сигнал звонка от контакта [contactId] (localId, не ключ). */
    data class Signal(val contactId: String, val message: AppMessage)

    private val _incoming = MutableSharedFlow<Signal>(
        extraBufferCapacity = INCOMING_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Сигналы звонков от контактов; только `CallOffer`/`CallAnswer`/`CallIceCandidate`/`CallEnd`. */
    val incoming: SharedFlow<Signal> = _incoming.asSharedFlow()

    private class Outgoing(
        val contactId: String,
        val callId: String,
        val message: AppMessage,
        val attempts: Int,
        val result: CompletableDeferred<Boolean>
    )

    private val queue = Channel<Outgoing>(Channel.UNLIMITED)

    /** callId звонков, чьи ещё не отправленные сигналы (кроме `CallEnd`) больше никому не нужны. */
    private val cancelledCalls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        scope.launch {
            for (item in queue) {
                val delivered = try {
                    deliver(item)
                } catch (e: CancellationException) {
                    item.result.complete(false)
                    throw e
                } catch (e: Exception) {
                    false
                }
                item.result.complete(delivered)
            }
        }
    }

    // ---------------------------------------------------------------- приём

    /**
     * Точка входа для `IncomingMessageProcessor`. Не-сигналы игнорируются, чтобы вызывающему не нужно
     * было знать, какие типы сообщений относятся к звонкам. Не блокирует и не бросает исключений.
     */
    fun onIncoming(contactId: String, message: AppMessage) {
        if (!message.isCallSignal()) return
        _incoming.tryEmit(Signal(contactId, message))
    }

    // -------------------------------------------------------------- отправка

    /** `true` в результате — сигнал доставлен; `false` — не удалось за все попытки (или звонок отменён). */
    fun sendOffer(contactId: String, callId: String, sdp: String, withVideo: Boolean): Deferred<Boolean> =
        enqueue(contactId, callId, AppMessage.CallOffer(callId, sdp, withVideo), OFFER_ATTEMPTS)

    fun sendAnswer(contactId: String, callId: String, sdp: String): Deferred<Boolean> =
        enqueue(contactId, callId, AppMessage.CallAnswer(callId, sdp), ANSWER_ATTEMPTS)

    fun sendIceCandidate(
        contactId: String,
        callId: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ): Deferred<Boolean> = enqueue(
        contactId, callId, AppMessage.CallIceCandidate(callId, sdpMid, sdpMLineIndex, candidate), ICE_ATTEMPTS
    )

    fun sendEnd(
        contactId: String,
        callId: String,
        reason: AppMessage.CallEnd.Reason = AppMessage.CallEnd.Reason.HANGUP
    ): Deferred<Boolean> = enqueue(contactId, callId, AppMessage.CallEnd(callId, reason), END_ATTEMPTS)

    /**
     * Звонок [callId] завершён локально: неотправленные offer/answer/кандидаты этого звонка пропускаются
     * (не тратим соединения и не задерживаем `CallEnd` повторами к уже ненужному собеседнику).
     * Уже поставленный в очередь `CallEnd` по-прежнему уходит.
     */
    fun cancelCall(callId: String) {
        cancelledCalls.add(callId)
    }

    private fun enqueue(contactId: String, callId: String, message: AppMessage, attempts: Int): Deferred<Boolean> {
        val result = CompletableDeferred<Boolean>()
        // Новый звонок с тем же callId невозможен (UUIDv4), но если очередь уже закрыта — просто «не вышло».
        if (queue.trySend(Outgoing(contactId, callId, message, attempts, result)).isFailure) result.complete(false)
        return result
    }

    private suspend fun deliver(item: Outgoing): Boolean {
        val isEnd = item.message is AppMessage.CallEnd
        if (!isEnd && item.callId in cancelledCalls) return false

        val payload = item.message.encode()
        for (attempt in 1..item.attempts) {
            val contact = contactRepository.getContact(item.contactId) ?: return false
            val sent = try {
                transport.sendEncryptedPacket(contact, payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false // транспорт не должен бросать, но сигналинг не должен падать от его сбоя
            }
            if (sent) {
                if (isEnd) cancelledCalls.remove(item.callId)
                return true
            }
            if (attempt < item.attempts) {
                delay(retryDelayMillis)
                if (!isEnd && item.callId in cancelledCalls) return false
            }
        }
        if (isEnd) cancelledCalls.remove(item.callId)
        return false
    }

    companion object {
        const val DEFAULT_RETRY_DELAY_MS = 500L

        // Offer — одна попытка: звонящий должен быстро узнать, что собеседник недоступен.
        private const val OFFER_ATTEMPTS = 1
        private const val ANSWER_ATTEMPTS = 3
        private const val ICE_ATTEMPTS = 2
        private const val END_ATTEMPTS = 2
        private const val INCOMING_BUFFER = 64
    }
}

private fun AppMessage.isCallSignal(): Boolean =
    this is AppMessage.CallOffer || this is AppMessage.CallAnswer ||
        this is AppMessage.CallIceCandidate || this is AppMessage.CallEnd
