package com.ramka.domain.repository

/**
 * Хранилище принимаемых файлов (этап 6.4): временный файл на время сборки и публикация готового
 * файла в общедоступное место. Реализация — в app-слое (файлы, MediaStore); domain о них не знает.
 * [transferId] — локальный id входящего сообщения (UUID, придуман приёмником, не отправителем).
 */
interface IncomingFileStore {
    /** Создаёт пустой временный файл передачи (или обнуляет старый). false — не удалось. */
    suspend fun begin(transferId: String): Boolean

    /** Пишет [data] во временный файл по смещению [offset] (порядок чанков не важен). false — ошибка записи. */
    suspend fun write(transferId: String, offset: Long, data: ByteArray): Boolean

    /**
     * Переносит собранный файл в общедоступное хранилище и удаляет временный.
     * @return Uri сохранённого файла (content:// MediaStore или file:// на старых Android) или null при сбое.
     */
    suspend fun publish(transferId: String, displayName: String, mimeType: String): String?

    /** Удаляет временный файл передачи (сбой, прерывание, повторный старт). */
    suspend fun discard(transferId: String)

    /** Удаляет ВСЕ временные файлы: на старте процесса активных передач нет, значит, это осиротевшие остатки. */
    suspend fun discardAll()
}
