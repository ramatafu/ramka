package com.ramka.app.connection

/** Тип активного соединения для индикатора на логотипе. */
enum class ConnectionState { P2P, TURN, RELAY, OFFLINE }

/** Вид текущей сети устройства. */
enum class NetworkKind {
    NONE,
    LOCAL,
    OTHER
}

/**
 * Единое правило выбора состояния.
 *
 * Приоритеты (сверху вниз):
 * 1. Нет сети → OFFLINE
 * 2. Идёт звонок → TURN (активный вызов всегда приоритетнее)
 * 3. Wi-Fi/Ethernet И обнаружен пир в LAN → P2P (зеленый)
 * 4. Регистрация на релее активна → RELAY (синий) ← ПОДНЯЛИ ВЫШЕ
 * 5. Хотя бы один TURN-сервер имеет статус WORKING → TURN (желтый) ← ОПУСТИЛИ НИЖЕ
 * 6. Иначе → OFFLINE (серый)
 */
fun resolveConnectionState(
    callConnected: Boolean,
    network: NetworkKind,
    relayConnected: Boolean,
    peersDiscoveredInLan: Boolean,
    turnReady: Boolean
): ConnectionState = when {
    network == NetworkKind.NONE -> ConnectionState.OFFLINE
    callConnected -> ConnectionState.TURN
    network == NetworkKind.LOCAL && peersDiscoveredInLan -> ConnectionState.P2P
    relayConnected -> ConnectionState.RELAY // ← ТЕПЕРЬ RELAY ПРИОРИТЕТНЕЕ TURN
    turnReady -> ConnectionState.TURN       // ← ТЕПЕРЬ TURN НИЖЕ RELAY
    else -> ConnectionState.OFFLINE
}