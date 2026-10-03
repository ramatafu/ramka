package com.ramka.network.relay

import com.ramka.crypto.keys.KeyManager

/** [RelayIdentity] поверх долговременного Ed25519-ключа устройства ([KeyManager]). */
class KeyManagerRelayIdentity(private val keyManager: KeyManager) : RelayIdentity {
    override val ed25519Public: ByteArray
        get() = keyManager.getOrCreateIdentity().ed25519Public

    override fun sign(message: ByteArray): ByteArray = keyManager.signWithIdentity(message)
}
