package com.ramka.domain.util

import com.ramka.domain.model.AppMessage

/**
 * Параметры передачи файлов (этап 6.4). Одно соединение несёт ровно один пакет (см.
 * SecureLanChannel), поэтому на КАЖДЫЙ чанк приходится полное IK-рукопожатие — чем меньше
 * чанк, тем больше накладных расходов; 64 КиБ взяты по заданию.
 */
object FileTransferConfig {
    /** Размер выровненного пакета чанка на проводе: ровно 64 КиБ (кратно FramePadding.LARGE_STEP). */
    private const val WIRE_PACKET_SIZE = 64 * 1024

    /** Заголовок выравнивания FramePadding (1 байт флагов + 4 байта длины), см. crypto-модуль. */
    private const val PADDING_HEADER_SIZE = 5

    /**
     * Данные в чанке подобраны так, чтобы пакет (заголовок выравнивания + тег + UUID + индекс +
     * данные) заполнял 64 КиБ без остатка: иначе выравнивание округлило бы его до 68 КиБ.
     */
    const val CHUNK_SIZE: Int = WIRE_PACKET_SIZE - PADDING_HEADER_SIZE - AppMessage.FILE_CHUNK_OVERHEAD

    /** Верхняя граница размера файла (и на отправке, и на приёме). */
    const val MAX_FILE_SIZE_BYTES: Long = 50L * 1024 * 1024

    /** Принимаемый размер чанка из метаданных: защита от абсурдных значений. */
    const val MAX_ACCEPTED_CHUNK_SIZE: Int = 1024 * 1024

    /** Сколько раз пробуем отправить один пакет (метаданные или чанк), прежде чем считать передачу проваленной. */
    const val SEND_ATTEMPTS_PER_PACKET = 3

    /** Пауза перед повтором пакета: умножается на номер неудавшейся попытки. */
    const val RETRY_PAUSE_MILLIS = 400L

    /** Одновременно принимаемых файлов (все контакты вместе). */
    const val MAX_ACTIVE_INCOMING = 3

    /**
     * Приём без новых чанков дольше этого времени считается брошенным отправителем: при нехватке
     * слотов (см. [MAX_ACTIVE_INCOMING]) он помечается FAILED и освобождает слот.
     */
    const val STALE_INCOMING_MILLIS = 5L * 60 * 1000

    fun chunkCount(totalSize: Long, chunkSize: Int = CHUNK_SIZE): Int {
        require(totalSize >= 0 && chunkSize > 0)
        return ((totalSize + chunkSize - 1) / chunkSize).toInt()
    }
}
