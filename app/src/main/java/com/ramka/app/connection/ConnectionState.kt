package com.ramka.app.connection

/** Тип активного соединения для индикатора на логотипе. */
enum class ConnectionState { P2P, TURN, RELAY, OFFLINE }

/** Вид текущей сети устройства. */
enum class NetworkKind {
    /** Нет сети. */
    NONE,

    /** Wi-Fi или Ethernet: LAN-слушатель и прямая доставка по локальной сети возможны. */
    LOCAL,

    /** Мобильная сеть и прочее: прямой LAN-доставки нет. */
    OTHER
}

/**
 * Единое правило выбора состояния (чистая функция, без Android).
 *
 * Приоритеты:
 * 1. Нет сети → OFFLINE (даже если релей или звонок «числятся» подключёнными: это устаревшее состояние).
 * 2. Идёт звонок (CallState.Connected) → TURN: звонки в приложении сейчас принудительно relay-only.
 * 3. Wi-Fi/Ethernet И обнаружен хотя бы один пир в LAN → P2P (зеленый).
 * 4. Регистрация хотя бы на одном релее активна → RELAY (синий).
 * 5. Иначе → OFFLINE (серый, например, после сброса приложения или в чужом Wi-Fi без пиров).
 */
fun resolveConnectionState(
    callConnected: Boolean,
    network: NetworkKind,
    relayConnected: Boolean,
    peersDiscoveredInLan: Boolean
): ConnectionState = when {
    network == NetworkKind.NONE -> ConnectionState.OFFLINE
    callConnected -> ConnectionState.TURN
    network == NetworkKind.LOCAL && peersDiscoveredInLan -> ConnectionState.P2P
    relayConnected -> ConnectionState.RELAY
    else -> ConnectionState.OFFLINE
}