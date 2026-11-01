package com.ramka.domain.util

import com.ramka.domain.model.TransportChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * По какому каналу последний раз реально ушёл пакет контакту (в памяти). Заполняет транспорт после каждой
 * отправки: успех — канал, полный провал — null (канала сейчас нет). Нужен только для индикации в чате.
 */
class ActiveChannelTracker {
    private val state = MutableStateFlow<Map<String, TransportChannel>>(emptyMap())
    val channels: StateFlow<Map<String, TransportChannel>> = state.asStateFlow()

    fun report(contactId: String, channel: TransportChannel?) {
        state.update { current ->
            when {
                channel == null && contactId !in current -> current
                channel == null -> current - contactId
                current[contactId] == channel -> current
                else -> current + (contactId to channel)
            }
        }
    }

    fun channelFor(contactId: String): TransportChannel? = state.value[contactId]
}
