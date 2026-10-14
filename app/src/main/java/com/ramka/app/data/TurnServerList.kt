package com.ramka.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Чистые операции над списком серверов (без Android и DataStore — тестируются обычным JUnit). */
internal object TurnServerList {

    /** Сортирует по [TurnServerEntry.order] и делает порядок плотным: 0..n-1. */
    fun normalize(list: List<TurnServerEntry>): List<TurnServerEntry> =
        list.sortedBy { it.order }.mapIndexed { index, e -> e.copy(order = index) }

    fun add(list: List<TurnServerEntry>, entry: TurnServerEntry): List<TurnServerEntry> {
        val base = normalize(list)
        return base + entry.copy(order = base.size)
    }

    fun remove(list: List<TurnServerEntry>, id: String): List<TurnServerEntry> =
        normalize(list.filterNot { it.id == id })

    /** Заменяет запись с тем же id; позиция остаётся прежней (порядок меняется только moveUp/moveDown). */
    fun update(list: List<TurnServerEntry>, entry: TurnServerEntry): List<TurnServerEntry> =
        normalize(list).map { if (it.id == entry.id) entry.copy(order = it.order) else it }

    fun moveUp(list: List<TurnServerEntry>, id: String): List<TurnServerEntry> = swap(list, id, -1)

    fun moveDown(list: List<TurnServerEntry>, id: String): List<TurnServerEntry> = swap(list, id, +1)

    private fun swap(list: List<TurnServerEntry>, id: String, delta: Int): List<TurnServerEntry> {
        val base = normalize(list)
        val from = base.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in base.indices) return base
        val mutable = base.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        return normalize(mutable.mapIndexed { index, e -> e.copy(order = index) })
    }
}

/** JSON-представление списка для DataStore. Пароля в нём нет по построению. */
internal object TurnServerCodec {

    fun encode(list: List<TurnServerEntry>): String {
        val array = JSONArray()
        for (e in list) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("url", e.url)
                    .put("username", e.username)
                    .put("enabled", e.isEnabled)
                    .put("order", e.order)
            )
        }
        return array.toString()
    }

    /** Битые данные и битые записи не роняют приложение: пустой список / пропуск записи. */
    fun decode(raw: String?): List<TurnServerEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val url = o.optString("url")
                    if (id.isBlank() || url.isBlank()) continue
                    add(
                        TurnServerEntry(
                            id = id,
                            url = url,
                            username = o.optString("username"),
                            isEnabled = o.optBoolean("enabled", true),
                            order = o.optInt("order", i)
                        )
                    )
                }
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
