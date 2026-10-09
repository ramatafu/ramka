package com.ramka.app.relay

import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayAddressParseResult
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPin

/**
 * Снимок старых одиночных настроек релея (шаги <= 2). address — сырая строка «host:port»,
 * как она лежала в SharedPreferences до шага 3 (см. AppPreferences.legacyRelaySnapshot).
 */
data class LegacyRelaySnapshot(
    val enabled: Boolean,
    val address: String?,
    val token: String?,
    val pin: String?
)

/**
 * Чистая миграция «старые одиночные настройки -> одна запись пула». Не зависит от
 * RelaySettings/RelayConfig: работает только со снимком. Повреждённые данные не
 * мигрируются (возвращается null), старые ключи при этом всё равно очищаются вызывающим.
 */
object RelayPoolMigrator {

    const val LEGACY_ENTRY_ID = "legacy"

    fun migrate(snapshot: LegacyRelaySnapshot?): RelayEntry? {
        if (snapshot == null) return null
        val addressText = snapshot.address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val token = snapshot.token?.trim() ?: return null
        if (token.toByteArray(Charsets.UTF_8).size !in RelayEntry.TOKEN_MIN_BYTES..RelayEntry.TOKEN_MAX_BYTES) return null
        val address = (RelayAddress.parse(addressText) as? RelayAddressParseResult.Success)?.address ?: return null
        val pin = snapshot.pin?.trim()?.takeIf { it.isNotEmpty() }?.let { RelayPin.normalize(it) }
        if (pin == null && address.isIpLiteral) return null // старый ввод не проходит новые правила
        return RelayEntry(LEGACY_ENTRY_ID, address, token, pin, "")
    }
}