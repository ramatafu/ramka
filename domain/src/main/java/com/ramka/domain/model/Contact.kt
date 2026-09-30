package com.ramka.domain.model

import java.util.Arrays

/**
 * Локальная запись о контакте. Не имеет глобального идентификатора —
 * существует только на устройстве владельца.
 *
 * @property localId локальный идентификатор записи (не передаётся по сети).
 * @property alias локальный псевдоним, задаётся пользователем самостоятельно.
 * @property publicKey публичный ключ контакта для обмена ключами (X25519), зафиксированный при добавлении.
 * @property signingPublicKey публичный ключ контакта для проверки подписи рукопожатия (Ed25519).
 *   Обязателен для аутентификации в IK-рукопожатии (см. crypto/noise/IkHandshake) — без него
 *   защищённое соединение с контактом установить нельзя.
 * @property lastKnownAddress временный сетевой адрес для доставки (LAN IP:port или relay-хинт).
 * @property verification статус верификации ключа контакта.
 * @property addedAtEpochDay день добавления контакта (без точного времени, см. п. 3.5 спецификации).
 * @property unreadCount счётчик непрочитанных входящих сообщений этого контакта (UX-правка,
 *   не в исходной спеке). Растёт на каждое новое (не дублирующее) входящее сообщение
 *   (см. HandleIncomingTextUseCase) и обнуляется при открытии чата вместе с READ-ACK.
 */
data class Contact(
    val localId: String,
    val alias: String,
    val publicKey: ByteArray,
    val signingPublicKey: ByteArray,
    val lastKnownAddress: NetworkAddress?,
    val verification: KeyVerificationStatus,
    val addedAtEpochDay: Long,
    val unreadCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        if (localId != other.localId) return false
        if (alias != other.alias) return false
        if (!Arrays.equals(publicKey, other.publicKey)) return false
        if (!Arrays.equals(signingPublicKey, other.signingPublicKey)) return false
        if (lastKnownAddress != other.lastKnownAddress) return false
        if (verification != other.verification) return false
        if (addedAtEpochDay != other.addedAtEpochDay) return false
        if (unreadCount != other.unreadCount) return false
        return true
    }

    override fun hashCode(): Int {
        var result = localId.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + Arrays.hashCode(publicKey)
        result = 31 * result + Arrays.hashCode(signingPublicKey)
        result = 31 * result + (lastKnownAddress?.hashCode() ?: 0)
        result = 31 * result + verification.hashCode()
        result = 31 * result + addedAtEpochDay.hashCode()
        result = 31 * result + unreadCount
        return result
    }
}

/** Статус верификации публичного ключа контакта. */
enum class KeyVerificationStatus {
    UNVERIFIED,
    VERIFIED_BY_QR,
    KEY_CHANGED_WARNING
}

/** Временный сетевой адрес контакта: локальный (LAN) либо через домашний relay. */
sealed class NetworkAddress {
    data class Lan(val host: String, val port: Int) : NetworkAddress()
    data class Relay(val relayUrl: String, val routingHint: String) : NetworkAddress()
}