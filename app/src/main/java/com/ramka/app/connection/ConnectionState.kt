package com.ramka.app.connection

/** Тип активного соединения для индикатора на логотипе. */
enum class ConnectionState { P2P, YGGDRASIL, TURN, RELAY, RELAY_PROXY, OFFLINE }

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
 * 4. Yggdrasil доступен (включён, интерфейс найден, есть контакты с Yggmail-адресом) → YGGDRASIL (фиолетовый)
 * 5. Регистрация на релее активна → RELAY (синий), а при включённом прокси → RELAY_PROXY (маджента)
 *    ← ПОДНЯЛИ ВЫШЕ. При включённом прокси fail-closed гарантирует, что релей подключён только через прокси
 *    (напрямую соединение не устанавливается), поэтому отдельный признак «туннель жив» не нужен.
 * 6. Хотя бы один TURN-сервер имеет статус WORKING → TURN (желтый) ← ОПУСТИЛИ НИЖЕ
 * 7. Иначе → OFFLINE (серый)
 */
fun resolveConnectionState(
    callConnected: Boolean,
    network: NetworkKind,
    relayConnected: Boolean,
    peersDiscoveredInLan: Boolean,
    turnReady: Boolean,
    yggdrasilActive: Boolean = false,
    /** Прокси включён в настройках; учитывается только в ветке релея и на остальные состояния не влияет. */
    proxyEnabled: Boolean = false
): ConnectionState = when {
    network == NetworkKind.NONE -> ConnectionState.OFFLINE
    callConnected -> ConnectionState.TURN
    network == NetworkKind.LOCAL && peersDiscoveredInLan -> ConnectionState.P2P
    yggdrasilActive -> ConnectionState.YGGDRASIL
    relayConnected && proxyEnabled -> ConnectionState.RELAY_PROXY
    relayConnected -> ConnectionState.RELAY // ← ТЕПЕРЬ RELAY ПРИОРИТЕТНЕЕ TURN
    turnReady -> ConnectionState.TURN       // ← ТЕПЕРЬ TURN НИЖЕ RELAY
    else -> ConnectionState.OFFLINE
}