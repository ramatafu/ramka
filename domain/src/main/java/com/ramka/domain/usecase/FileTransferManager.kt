package com.ramka.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Запускает передачи файлов в долгоживущем скоупе (приложения), а не в ViewModel: уход с экрана
 * чата не должен обрывать передачу. На одно сообщение — не больше одной активной передачи
 * (повторное нажатие «Повторить» во время передачи игнорируется).
 */
class FileTransferManager(
    private val scope: CoroutineScope,
    private val sendFile: SendFileUseCase
) {
    private val jobs = HashMap<String, Job>()

    /** @return true, если передача запущена; false — для этого сообщения она уже идёт. */
    fun start(messageLocalId: String): Boolean = synchronized(jobs) {
        if (jobs[messageLocalId]?.isActive == true) return false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                sendFile.transfer(messageLocalId)
            } finally {
                synchronized(jobs) { jobs.remove(messageLocalId) }
            }
        }
        jobs[messageLocalId] = job
        job.start()
        true
    }
}
