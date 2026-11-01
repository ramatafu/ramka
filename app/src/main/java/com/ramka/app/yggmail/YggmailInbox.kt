package com.ramka.app.yggmail

import com.ramka.domain.util.OfflinePacketSealer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Приём писем Yggmail. [pollOnce] забирает непрочитанные письма «Ramka-E2EE», вскрывает их через
 * [OfflinePacketSealer] и выдаёт в [packets] — этот поток подмешивается к входящим всех остальных каналов
 * (`CompositeTransportRepository.incomingPackets`), поэтому дальше работает обычная обработка входящих Ramka.
 *
 * Письмо помечается прочитанным только после того, как пакет действительно передан подписчику; если подписчика
 * нет (обработчик входящих ещё не запущен), опрос откладывается, и письмо дождётся следующего раза. Посторонний
 * или невскрываемый мусор отбрасывается и помечается прочитанным, чтобы не разбирать его снова.
 *
 * @param subscriberWaitMs сколько ждать подписчика потока: при холодном старте процесса из WorkManager
 *   обработчик входящих подписывается чуть позже воркера.
 */
class YggmailInbox(
    private val configSource: YggmailConfigSource,
    private val mail: YggmailMail,
    private val sealer: OfflinePacketSealer,
    private val enabled: () -> Boolean,
    private val log: (String) -> Unit = {},
    private val subscriberWaitMs: Long = 3_000L
) {
    private val shared = MutableSharedFlow<Pair<ByteArray, ByteArray>>(extraBufferCapacity = BUFFER)

    /** (публичный ключ отправителя, открытый пакет) — тот же вид, что у остальных транспортов. */
    val packets: Flow<Pair<ByteArray, ByteArray>> = shared.asSharedFlow()

    /** @return сколько писем принято; failure — ошибка почты (для воркера не повод для повтора). */
    suspend fun pollOnce(): Result<Int> {
        try {
            if (!enabled() || !sealer.isAvailable) return Result.success(0)
            val config = configSource.load()?.takeIf { it.isValid() } ?: return Result.success(0)
            if (!awaitSubscriber()) {
                log("Yggmail: некому отдавать входящие, опрос отложен")
                return Result.success(0)
            }
            return mail.receive(config) { body -> accept(body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Yggmail poll: ${e.javaClass.simpleName}")
            return Result.failure(e)
        } catch (e: LinkageError) {
            log("Yggmail poll: ${e.javaClass.simpleName}")
            return Result.failure(IllegalStateException("JavaMail недоступен", e))
        }
    }

    private suspend fun awaitSubscriber(): Boolean {
        var waited = 0L
        while (shared.subscriptionCount.value == 0) {
            if (waited >= subscriberWaitMs) return false
            delay(POLL_STEP_MS)
            waited += POLL_STEP_MS
        }
        return true
    }

    /** true — письмо обработано (в т.ч. отброшено как мусор) и может быть помечено прочитанным. */
    private suspend fun accept(body: String): Boolean {
        val sealed = YggmailPayload.decode(body) ?: return true
        val opened = try {
            sealer.open(sealed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return true
        // false: буфер переполнен — письмо останется непрочитанным и будет принято в следующий опрос.
        return shared.tryEmit(opened)
    }

    private companion object {
        const val BUFFER = 64
        const val POLL_STEP_MS = 100L
    }
}
