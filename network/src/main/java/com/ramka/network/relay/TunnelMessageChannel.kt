package com.ramka.network.local

import java.net.Socket

/**
 * Обмен одним сообщением по УЖЕ установленному сокету (этап 3: «труба» через релей).
 * Реализация — [SecureLanChannel]; интерфейс нужен, чтобы транспорт через релей тестировался без
 * криптографии и сети.
 */
interface TunnelMessageChannel {
    /** Рукопожатие (инициатор) и отправка одного сообщения. Сокет не закрывается. `false` — не вышло. */
    suspend fun sendMessageOver(
        socket: Socket,
        remoteStaticX25519: ByteArray,
        remoteSigningPublicKey: ByteArray,
        plaintext: ByteArray,
        timeoutMillis: Int = 5000
    ): Boolean

    /** Рукопожатие (ответчик) и приём одного сообщения. Сокет закрывается. `null` — сбой или фиктивный пакет. */
    suspend fun processIncomingSocket(socket: Socket): IncomingMessage?
}
