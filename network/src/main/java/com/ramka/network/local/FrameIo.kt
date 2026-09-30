package com.ramka.network.local

import com.ramka.network.protocol.Frame
import java.io.DataInputStream
import java.io.DataOutputStream

/** 4-байтная big-endian длина + байты кадра. Общий помощник для сервера и клиента. */
internal object FrameIo {
    const val MAX_FRAME_SIZE = 8 * 1024 * 1024 // 8 МБ — с запасом под будущие вложения

    fun write(output: DataOutputStream, frame: Frame) {
        val bytes = frame.encode()
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    fun read(input: DataInputStream): Frame {
        val length = input.readInt()
        require(length in 1..MAX_FRAME_SIZE) { "Некорректный размер кадра: $length" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return Frame.decode(bytes)
    }
}
