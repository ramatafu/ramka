package com.ramka.domain.util

import com.ramka.domain.model.AppMessage

/**
 * Что имеет смысл отправлять через медленный асинхронный канал (почта): текст, подтверждения и удаление.
 * Нельзя: звонки (оффер, дошедший через минуты, — «призрачный звонок») и файловые кадры (передача файла — это
 * десятки пакетов по 64 КБ, каждый стал бы отдельным письмом). Такой пакет канал просто не берёт, и
 * вызывающий код видит обычный отказ — как если бы Yggmail не существовало.
 */
object OfflineEligibility {
    const val MAX_PACKET_BYTES = 16 * 1024

    fun isEligible(packet: ByteArray): Boolean {
        if (packet.isEmpty() || packet.size > MAX_PACKET_BYTES) return false
        return when (AppMessage.decode(packet)) {
            is AppMessage.Text, is AppMessage.DeliveredAck, is AppMessage.ReadAck, is AppMessage.DeleteMessage -> true
            else -> false
        }
    }
}
