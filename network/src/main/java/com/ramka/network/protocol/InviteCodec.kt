package com.ramka.network.protocol

import android.util.Base64
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.usecase.Invite
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * Формат содержимого QR-приглашения. Содержит только то, что нужно для
 * добавления контакта: оба публичных ключа (X25519 для ECDH, Ed25519 для
 * проверки подписи рукопожатия), временный LAN-адрес и срок жизни.
 * Никаких постоянных идентификаторов — приглашение одноразовое (п. 3.8).
 */
@Serializable
private data class InviteWire(
    val x25519PublicKeyB64: String,
    val ed25519PublicKeyB64: String,
    val host: String?,
    val port: Int?,
    val expiresAtEpochMillis: Long,
    val nonceB64: String
)

object InviteCodec {

    private const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L // 10 минут

    fun createForSelf(x25519PublicKey: ByteArray, ed25519PublicKey: ByteArray, lanHost: String?, lanPort: Int?): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val wire = InviteWire(
            x25519PublicKeyB64 = Base64.encodeToString(x25519PublicKey, Base64.NO_WRAP),
            ed25519PublicKeyB64 = Base64.encodeToString(ed25519PublicKey, Base64.NO_WRAP),
            host = lanHost,
            port = lanPort,
            expiresAtEpochMillis = System.currentTimeMillis() + DEFAULT_TTL_MILLIS,
            nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
        return Json.encodeToString(wire)
    }

    fun parse(qrText: String): Invite {
        val wire = Json.decodeFromString<InviteWire>(qrText)
        val address = if (wire.host != null && wire.port != null) {
            NetworkAddress.Lan(wire.host, wire.port)
        } else null
        return Invite(
            publicKey = Base64.decode(wire.x25519PublicKeyB64, Base64.NO_WRAP),
            signingPublicKey = Base64.decode(wire.ed25519PublicKeyB64, Base64.NO_WRAP),
            address = address,
            expiresAtEpochMillis = wire.expiresAtEpochMillis,
            nonce = Base64.decode(wire.nonceB64, Base64.NO_WRAP)
        )
    }
}
