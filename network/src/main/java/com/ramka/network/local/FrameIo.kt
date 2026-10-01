package com.ramka.network.local

import com.ramka.crypto.noise.IkHandshake
import com.ramka.crypto.padding.FramePadding
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Проводной формат LAN-соединения без открытых сигнатур протокола (ЭТАП 2.5, шаг 3).
 *
 * Поток соединения целиком:
 *
 *   M1 (инициатор -> ответчик):  80 байт, как есть
 *   M2 (ответчик -> инициатор): 112 байт, как есть
 *   M3 (инициатор -> ответчик):  80 байт, как есть
 *   кадр данных (инициатор -> ответчик):
 *       [length: 4 байта big-endian][ciphertext: length байт]
 *
 * У кадров рукопожатия нет ни байта типа, ни поля длины: роль кадра определяется только
 * его позицией в рукопожатии (конечный автомат в [SecureLanChannel]), а размер фиксирован.
 * У кадра данных нет байта типа; `length` — длина шифртекста (выровненное тело + тег AEAD)
 * и обязана быть `корзина FramePadding + 16`, иначе соединение закрывается до чтения тела.
 *
 * Счётчика на проводе нет: одно соединение несёт ровно одно сообщение на свежих ключах, поэтому
 * номер пакета всегда [IMPLICIT_COUNTER] и обе стороны подставляют его сами (он же nonce AEAD).
 * Это согласовано с D-3/D-6: отправитель проверяет, что `SessionCipher` выдал именно этот номер,
 * иначе отправка отменяется, а не уходит с повторным nonce.
 */
internal object FrameIo {
    const val MESSAGE1_SIZE = IkHandshake.MESSAGE1_SIZE // 80
    const val MESSAGE2_SIZE = IkHandshake.MESSAGE2_SIZE // 112
    const val MESSAGE3_SIZE = IkHandshake.MESSAGE3_SIZE // 80

    const val LENGTH_FIELD_SIZE = 4
    const val AEAD_TAG_SIZE = 16

    /** Номер единственного пакета соединения; на провод не попадает (см. заголовок файла). */
    const val IMPLICIT_COUNTER = 0L

    /** Значение [readExact] для «без общего дедлайна»: действует только текущий `soTimeout` сокета. */
    const val NO_DEADLINE = Long.MIN_VALUE

    /** Допустимое значение поля `length` кадра данных. */
    fun isValidCiphertextLength(length: Int): Boolean =
        length >= AEAD_TAG_SIZE && FramePadding.isValidPaddedSize(length - AEAD_TAG_SIZE)

    /** Пишет кадр рукопожатия как есть. Размер обязан совпадать с фиксированным размером этого шага. */
    fun writeHandshake(output: OutputStream, bytes: ByteArray, expectedSize: Int) {
        require(bytes.size == expectedSize) { "Размер кадра рукопожатия ${bytes.size}, ожидалось $expectedSize" }
        output.write(bytes)
        output.flush()
    }

    /** Пишет кадр данных `[length][ciphertext]` одним `write`, чтобы поля не уходили отдельными сегментами. */
    fun writeData(output: OutputStream, ciphertext: ByteArray) {
        require(isValidCiphertextLength(ciphertext.size)) { "Недопустимый размер шифртекста: ${ciphertext.size}" }
        val frame = ByteArray(LENGTH_FIELD_SIZE + ciphertext.size)
        writeInt(frame, 0, ciphertext.size)
        System.arraycopy(ciphertext, 0, frame, LENGTH_FIELD_SIZE, ciphertext.size)
        output.write(frame)
        output.flush()
    }

    /**
     * Читает ровно [size] байт. Если [deadlineNanos] задан (по `System.nanoTime()`), перед каждым
     * чтением `soTimeout` сокета ставится равным остатку до дедлайна, так что общее время
     * ограничено, а не только простой между байтами (защита от медленной отдачи по байту).
     * Недобор -> [EOFException], истечение времени -> [SocketTimeoutException].
     */
    fun readExact(socket: Socket, input: InputStream, size: Int, deadlineNanos: Long = NO_DEADLINE): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            if (deadlineNanos != NO_DEADLINE) {
                val remainingMillis = (deadlineNanos - System.nanoTime() + 999_999L) / 1_000_000L
                if (remainingMillis <= 0) throw SocketTimeoutException("Таймаут рукопожатия")
                socket.soTimeout = remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            val n = input.read(buffer, offset, size - offset)
            if (n < 0) throw EOFException("Соединение закрыто на ${offset} из $size байт")
            offset += n
        }
        return buffer
    }

    /**
     * Читает кадр данных и возвращает шифртекст. Недопустимая длина -> [IllegalArgumentException]
     * ДО выделения памяти под шифртекст; вызывающий закрывает соединение. Таймаут чтения — текущий
     * `soTimeout` сокета.
     */
    fun readData(socket: Socket, input: InputStream): ByteArray {
        val header = readExact(socket, input, LENGTH_FIELD_SIZE)
        val length = readInt(header, 0)
        require(isValidCiphertextLength(length)) { "Недопустимая длина кадра данных: $length" }
        return readExact(socket, input, length)
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}
