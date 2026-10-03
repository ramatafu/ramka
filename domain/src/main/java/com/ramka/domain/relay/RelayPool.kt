package com.ramka.domain.relay

/** Почему операция над пулом отклонена. */
enum class RelayPoolError {
    /** В пуле уже [RelayPool.MAX_ENTRIES] записей. */
    LIMIT_REACHED,

    /** Релей с таким адресом уже есть. */
    DUPLICATE_ADDRESS,

    /** Записи с таким id нет. */
    NOT_FOUND,

    /** Запись уже первая (вверх) или последняя (вниз). */
    CANNOT_MOVE
}

sealed interface RelayPoolResult {
    data class Changed(val pool: RelayPool) : RelayPoolResult
    data class Rejected(val reason: RelayPoolError) : RelayPoolResult
}

/**
 * Пул релеёв: упорядоченный список записей и два общих переключателя. Порядок записей — их приоритет:
 * при отправке релеи пробуются сверху вниз (с учётом состояния здоровья, см. шаг 2).
 *
 * Модель неизменяемая: операции возвращают [RelayPoolResult], хранилище применяет и сохраняет результат.
 *
 * @property enabled «Использовать пул релеёв». ВЫКЛ — приложение вообще не обращается к релеям. Флаг
 *   может быть включён при пустом списке: запрет такого включения — правило экрана настроек, а
 *   фактическое состояние определяет [isActive].
 * @property batterySaver «Режим экономии батареи» (по умолчанию ВЫКЛ). Выключен: приём идёт на ВСЕХ
 *   релеях пула одновременно, поэтому сообщение дойдёт, каким бы релеем ни пользовался отправитель.
 *   Включён: приём держится на одном релее (первом доступном по порядку), остальные подключения не
 *   поддерживаются; отправка по-прежнему перебирает весь пул. Поведение реализуется на шаге 2.
 */
data class RelayPool(
    val enabled: Boolean = false,
    val batterySaver: Boolean = false,
    val entries: List<RelayEntry> = emptyList()
) {
    /** Записи, готовые к подключению (полные и корректные), в порядке приоритета. */
    val usableEntries: List<RelayEntry>
        get() = entries.filter { it.issue == null }

    /** Записи, которыми приложение реально пользуется: пул включён и запись полная. */
    val activeEntries: List<RelayEntry>
        get() = if (enabled) usableEntries else emptyList()

    /** Пул включён и в нём есть хотя бы одна рабочая запись. */
    val isActive: Boolean
        get() = activeEntries.isNotEmpty()

    fun add(entry: RelayEntry): RelayPoolResult {
        if (entries.size >= MAX_ENTRIES) return RelayPoolResult.Rejected(RelayPoolError.LIMIT_REACHED)
        if (entries.any { it.address == entry.address }) return RelayPoolResult.Rejected(RelayPoolError.DUPLICATE_ADDRESS)
        return RelayPoolResult.Changed(copy(entries = entries + entry))
    }

    /** Заменяет запись с тем же id (редактирование); место в списке сохраняется. */
    fun replace(entry: RelayEntry): RelayPoolResult {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index < 0) return RelayPoolResult.Rejected(RelayPoolError.NOT_FOUND)
        if (entries.any { it.id != entry.id && it.address == entry.address }) {
            return RelayPoolResult.Rejected(RelayPoolError.DUPLICATE_ADDRESS)
        }
        return RelayPoolResult.Changed(copy(entries = entries.toMutableList().also { it[index] = entry }))
    }

    /** Удаляет запись; если она была последней, пул выключается (включённый пустой пул бессмыслен). */
    fun remove(id: String): RelayPoolResult {
        if (entries.none { it.id == id }) return RelayPoolResult.Rejected(RelayPoolError.NOT_FOUND)
        val rest = entries.filter { it.id != id }
        return RelayPoolResult.Changed(copy(enabled = enabled && rest.isNotEmpty(), entries = rest))
    }

    fun moveUp(id: String): RelayPoolResult = move(id, -1)

    fun moveDown(id: String): RelayPoolResult = move(id, +1)

    private fun move(id: String, delta: Int): RelayPoolResult {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return RelayPoolResult.Rejected(RelayPoolError.NOT_FOUND)
        val target = index + delta
        if (target !in entries.indices) return RelayPoolResult.Rejected(RelayPoolError.CANNOT_MOVE)
        val list = entries.toMutableList()
        val moved = list.removeAt(index)
        list.add(target, moved)
        return RelayPoolResult.Changed(copy(entries = list))
    }

    companion object {
        /** Не больше пяти релеёв: столько же постоянных соединений держит приём (если не включена экономия). */
        const val MAX_ENTRIES = 5
    }
}
