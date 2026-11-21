package com.ramka.domain.usecase

import com.ramka.domain.util.FileRetryTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Запускает передачи файлов в долгоживущем скоупе (приложения), а не в ViewModel: уход с экрана
 * чата не должен обрывать передачу. На одно сообщение — не больше одной активной передачи
 * (повторное нажатие «Повторить» во время передачи игнорируется).
 *
 * Автоповтор при восстановлении связи ([startAutoRetry]): не больше [FileRetryTracker] попыток на сообщение, без
 * сигнала собеседнику. Любой ручной запуск ([start]) сбрасывает этот счёт.
 */
class FileTransferManager(
    private val scope: CoroutineScope,
    private val sendFile: SendFileUseCase,
    private val retryTracker: FileRetryTracker = FileRetryTracker()
) {
    private val jobs = HashMap<String, Job>()

    /** Ручной запуск (первая отправка, «Повторить»). @return true, если передача запущена; false — для этого сообщения она уже идёт. */
    fun start(messageLocalId: String): Boolean = launchTransfer(messageLocalId, auto = false) != null

    /**
     * Автоматический повтор. @return запущенная передача либо null: для сообщения передача уже идёт или
     * лимит автопопыток исчерпан (тогда остаётся FAILED до ручного «Повторить»). Занятая передача попытку не тратит.
     */
    fun startAutoRetry(messageLocalId: String): Job? = launchTransfer(messageLocalId, auto = true)

    private fun launchTransfer(messageLocalId: String, auto: Boolean): Job? = synchronized(jobs) {
        if (jobs[messageLocalId]?.isActive == true) return null
        if (auto) {
            if (!retryTracker.tryAcquire(messageLocalId)) return null
        } else {
            retryTracker.reset(messageLocalId)
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (sendFile.transfer(messageLocalId, notifyPeer = !auto)) retryTracker.reset(messageLocalId)
            } finally {
                synchronized(jobs) { jobs.remove(messageLocalId) }
            }
        }
        jobs[messageLocalId] = job
        job.start()
        job
    }
}
