package com.ramka.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Живой прогресс активных передач (localId сообщения -> 0..100), только в памяти. В БД
 * прогресс пишется лишь в конце (успех/сбой): обновлять Room после каждого чанка не нужно.
 * Запись есть, пока передача идёт; UI подмешивает её поверх значения из БД.
 */
class FileTransferTracker {
    private val state = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = state.asStateFlow()

    fun update(messageLocalId: String, percent: Int) {
        val p = percent.coerceIn(0, 100)
        state.update { it + (messageLocalId to p) }
    }

    fun clear(messageLocalId: String) {
        state.update { it - messageLocalId }
    }

    fun isActive(messageLocalId: String): Boolean = messageLocalId in state.value
}
