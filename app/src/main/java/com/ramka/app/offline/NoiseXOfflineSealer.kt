package com.ramka.app.offline

import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.offline.OfflineSeal
import com.ramka.domain.model.Contact
import com.ramka.domain.util.OfflinePacketSealer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Рабочая схема запечатывания для офлайн-каналов (Yggmail): тонкая обёртка над [OfflineSeal] из модуля `crypto`.
 * Сама криптография (X25519, HKDF, ChaCha20-Poly1305, выравнивание) — там; здесь только выбор ключей контакта и
 * перевод в интерфейс домена. Чтение ключей идёт через EncryptedSharedPreferences (блокирующий ввод-вывод), поэтому
 * всё выполняется на `Dispatchers.IO`.
 *
 * Отправитель подтверждается самой криптографией: [open] отдаёт тот статический ключ, которым пакет реально
 * запечатан. Принадлежит ли он известному контакту, проверяет обработчик входящих (пакеты от неизвестных
 * ключей он отбрасывает), как и для остальных каналов.
 */
class NoiseXOfflineSealer(private val keyManager: KeyManager) : OfflinePacketSealer {

    override val isAvailable: Boolean = true

    override suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray? {
        // «Избранное» не имеет ключей и по сети не уходит.
        if (recipient.isSelf) return null
        return withContext(Dispatchers.IO) { OfflineSeal.seal(keyManager, recipient.publicKey, plaintext) }
    }

    override suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>? =
        withContext(Dispatchers.IO) {
            OfflineSeal.open(keyManager, sealed)?.let { it.senderStaticPublic to it.plaintext }
        }
}
