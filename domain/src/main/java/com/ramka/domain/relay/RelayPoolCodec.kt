package com.ramka.domain.relay

/**
 * Формат хранения пула релеёв: один JSON-документ. В приложении лежит одним зашифрованным значением
 * (SecureKeyStore), потому что содержит токены доступа.
 *
 * ```
 * {"v":1,"enabled":true,"batterySaver":false,
 *  "entries":[{"id":"…","label":"","address":"host:port","token":"…","pin":"64hex"|null}]}
 * ```
 *
 * Чтение терпимо к повреждениям отдельных записей (битая запись пропускается, остальные живут), но
 * не угадывает: документ неизвестной версии или не JSON — это `null`, и вызывающий решает, что делать.
 */
object RelayPoolCodec {

    const val VERSION = 1

    fun encode(pool: RelayPool): String = MiniJson.stringify(
        linkedMapOf<String, Any?>(
            "v" to VERSION,
            "enabled" to pool.enabled,
            "batterySaver" to pool.batterySaver,
            "entries" to pool.entries.map { e ->
                linkedMapOf<String, Any?>(
                    "id" to e.id,
                    "label" to e.label,
                    "address" to e.address.format(),
                    "token" to e.token,
                    "pin" to e.pinSha256
                )
            }
        )
    )

    /**
     * @param newId источник id для записей без корректного id.
     * @return пул или null, если текст — не JSON-объект поддерживаемой версии.
     */
    fun decode(text: String, newId: () -> String): RelayPool? {
        val root = try {
            MiniJson.parse(text) as? Map<*, *>
        } catch (e: MiniJson.ParseException) {
            null
        } ?: return null

        if ((root["v"] as? Long)?.toInt() != VERSION) return null

        val entries = ArrayList<RelayEntry>()
        val rawEntries = root["entries"] as? List<*> ?: emptyList<Any?>()
        for (raw in rawEntries) {
            if (entries.size >= RelayPool.MAX_ENTRIES) break
            val item = raw as? Map<*, *> ?: continue
            val address = (item["address"] as? String)?.let { RelayAddress.parseOrNull(it) } ?: continue
            if (entries.any { it.address == address }) continue // дубликаты адреса: остаётся первый

            val id = (item["id"] as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
                ?.takeIf { candidate -> entries.none { it.id == candidate } }
                ?: newId()
            val token = item["token"] as? String ?: ""
            val pin = (item["pin"] as? String)?.let { RelayPin.normalize(it) }
            val label = (item["label"] as? String).orEmpty().trim()
                .filter { !it.isISOControl() }
                .take(RelayEntry.MAX_LABEL_LENGTH)
            entries.add(RelayEntry(id, address, token, pin, label))
        }

        return RelayPool(
            enabled = root["enabled"] as? Boolean ?: false,
            batterySaver = root["batterySaver"] as? Boolean ?: false,
            entries = entries
        )
    }

    private const val MAX_ID_LENGTH = 64
}
