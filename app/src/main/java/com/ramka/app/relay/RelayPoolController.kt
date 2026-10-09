package com.ramka.app.relay

import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPool
import javax.inject.Inject
import javax.inject.Singleton

/** Состояние пула для UI (шаг 4 будет читать его напрямую). */
data class RelayPoolUiState(
    val enabled: Boolean,
    val entries: List<RelayEntry>
) {
    /** При пустом списке переключатель заблокирован. */
    val toggleAvailable: Boolean get() = entries.isNotEmpty()
    val toggleHint: String? get() = if (entries.isEmpty()) TOGGLE_HINT_EMPTY else null

    companion object {
        const val TOGGLE_HINT_EMPTY = "Добавьте хотя бы один релей"
    }
}

/**
 * Управление пулом релеёв: список, общий переключатель, add/replace/remove/move.
 * Вся запись идёт через [RelayPoolStore]; контроллер не знает ни о UI, ни о хранилище деталей.
 *
 * Правила переключателя: включить при пустом списке нельзя; удаление последнего релея
 * при включённом переключателе выключает его автоматически; выключенный пул не даёт
 * подключений вообще (приём/отправка/проверки гасит монитор и провайдер по `pool.enabled`).
 */
@Singleton
class RelayPoolController @Inject constructor(
    private val store: RelayPoolStore
) {

    fun current(): RelayPool = store.pool.value

    fun uiState(): RelayPoolUiState = current().let { RelayPoolUiState(it.enabled, it.entries) }

    /** @return true если состояние изменилось; false если включение запрещено (пустой пул). */
    fun setPoolEnabled(enabled: Boolean): Boolean {
        val p = current()
        if (enabled && p.entries.isEmpty()) return false
        if (p.enabled == enabled) return true
        store.save(p.copy(enabled = enabled))
        return true
    }

    /** @return false если адрес уже занят в пуле. */
    fun add(entry: RelayEntry): Boolean {
        val p = current()
        if (p.entries.any { it.address == entry.address }) return false
        store.save(p.copy(entries = p.entries + entry))
        return true
    }

    /** @return false если записи нет или её адрес занят другой записью. */
    fun replace(entry: RelayEntry): Boolean {
        val p = current()
        if (p.entries.none { it.id == entry.id }) return false
        if (p.entries.any { it.id != entry.id && it.address == entry.address }) return false
        store.save(p.copy(entries = p.entries.map { if (it.id == entry.id) entry else it }))
        return true
    }

    /** Удаление последнего релея автоматически выключает переключатель пула. */
    fun remove(id: String): Boolean {
        val p = current()
        val rest = p.entries.filterNot { it.id == id }
        if (rest.size == p.entries.size) return false
        store.save(p.copy(entries = rest, enabled = if (rest.isEmpty()) false else p.enabled))
        return true
    }

    fun moveUp(id: String): Boolean = move(id, -1)
    fun moveDown(id: String): Boolean = move(id, +1)

    private fun move(id: String, delta: Int): Boolean {
        val p = current()
        val i = p.entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        val j = i + delta
        if (j < 0 || j >= p.entries.size) return false
        val list = p.entries.toMutableList()
        val e = list.removeAt(i)
        list.add(j, e)
        store.save(p.copy(entries = list))
        return true
    }
}