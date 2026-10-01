package com.ramka.crypto.padding

/**
 * Выравнивание размера полезной нагрузки до фиксированных «корзин» (ЭТАП 2.5, шаг 1).
 *
 * Зачем: длина зашифрованного кадра видна наблюдателю сети. Без выравнивания
 * DELIVERED-ACK/READ-ACK (ровно 37 байт `AppMessage`) отличаются от текста по размеру,
 * а длина текста известна до байта. После выравнивания любое тело до [BUCKETS].last()
 * минус заголовок попадает в одну из немногих корзин, и ACK неотличим от короткого текста.
 *
 * Формат (применяется к plaintext ДО шифрования, то есть целиком внутри AEAD):
 *
 *   [flags: 1 байт][payloadLength: 4 байта big-endian][payload][нули до размера корзины]
 *
 * Размер результата — наименьшая корзина из [BUCKETS], вмещающая заголовок + payload;
 * выше последней корзины — кратно [LARGE_STEP]. Формат каноничен: [unpad] отвергает
 * любые отклонения (неверный размер, ненулевой хвост, неизвестные флаги), чтобы у
 * одного и того же payload было ровно одно допустимое представление.
 *
 * Это НЕ криптография и секретности не добавляет: хвост из нулей защищён тем же AEAD,
 * что и остальное тело. Класс чистый (без Android), не зависит от [AppMessage] и
 * транспорта.
 */
object FramePadding {

    /** Размер заголовка: 1 байт флагов + 4 байта длины. */
    const val HEADER_SIZE = 5

    /** Обычное тело. */
    const val FLAG_NONE: Int = 0x00

    /**
     * Зарезервировано под фиктивный пакет (этап 2.5, шаг 7, опционально). Приёмник
     * обязан молча отбросить такое тело. Пока нигде не выставляется.
     */
    const val FLAG_COVER: Int = 0x01

    private const val KNOWN_FLAGS_MASK = FLAG_COVER

    /** Корзины для малых тел. */
    val BUCKETS: IntArray = intArrayOf(128, 256, 512, 1024, 2048, 4096)

    /** Выше последней корзины размер округляется вверх до кратного этому значению. */
    const val LARGE_STEP = 4096

    /**
     * Верхняя граница размера результата. Кратна [LARGE_STEP]; вместе с тегом AEAD
     * (16 байт) укладывается в 8 МБ — верхнюю границу поля длины кадра данных.
     */
    const val MAX_PADDED_SIZE = 8 * 1024 * 1024 - LARGE_STEP

    const val MAX_PAYLOAD_SIZE = MAX_PADDED_SIZE - HEADER_SIZE

    /**
     * Является ли [size] допустимым размером выровненного тела: одна из [BUCKETS]
     * либо кратное [LARGE_STEP] выше последней корзины, не больше [MAX_PADDED_SIZE].
     * Сетевой уровень проверяет по этому предикату поле длины кадра данных ДО чтения
     * шифртекста и выделения памяти (ЭТАП 2.5, шаг 3).
     */
    fun isValidPaddedSize(size: Int): Boolean {
        if (size in BUCKETS) return true
        return size > BUCKETS.last() && size <= MAX_PADDED_SIZE && size % LARGE_STEP == 0
    }

    /** Результат [unpad]. */
    class Unpadded(val flags: Int, val payload: ByteArray)

    /** Размер, до которого будет выровнено тело длиной [payloadSize]. */
    fun paddedSize(payloadSize: Int): Int {
        require(payloadSize in 0..MAX_PAYLOAD_SIZE) { "Недопустимый размер тела: $payloadSize" }
        val total = HEADER_SIZE + payloadSize
        for (bucket in BUCKETS) {
            if (total <= bucket) return bucket
        }
        return ((total + LARGE_STEP - 1) / LARGE_STEP) * LARGE_STEP
    }

    /** Оборачивает [payload] в выровненное тело. Входной массив не изменяется. */
    fun pad(payload: ByteArray, flags: Int = FLAG_NONE): ByteArray {
        require(flags and KNOWN_FLAGS_MASK.inv() == 0) { "Неизвестные флаги: $flags" }
        val out = ByteArray(paddedSize(payload.size)) // хвост заполнен нулями
        out[0] = flags.toByte()
        val length = payload.size
        out[1] = (length ushr 24).toByte()
        out[2] = (length ushr 16).toByte()
        out[3] = (length ushr 8).toByte()
        out[4] = length.toByte()
        System.arraycopy(payload, 0, out, HEADER_SIZE, payload.size)
        return out
    }

    /**
     * Снимает выравнивание. Возвращает null, если тело не в каноничном формате
     * (вызывающий код должен его отбросить, а не падать).
     */
    fun unpad(padded: ByteArray): Unpadded? {
        if (padded.size < HEADER_SIZE || padded.size > MAX_PADDED_SIZE) return null

        val flags = padded[0].toInt() and 0xFF
        if (flags and KNOWN_FLAGS_MASK.inv() != 0) return null

        val length = ((padded[1].toInt() and 0xFF) shl 24) or
            ((padded[2].toInt() and 0xFF) shl 16) or
            ((padded[3].toInt() and 0xFF) shl 8) or
            (padded[4].toInt() and 0xFF)
        if (length < 0 || length > padded.size - HEADER_SIZE) return null

        // Каноничность: размер обязан быть ровно тем, который выбрал бы pad().
        if (paddedSize(length) != padded.size) return null

        for (i in HEADER_SIZE + length until padded.size) {
            if (padded[i].toInt() != 0) return null
        }

        return Unpadded(flags, padded.copyOfRange(HEADER_SIZE, HEADER_SIZE + length))
    }
}
