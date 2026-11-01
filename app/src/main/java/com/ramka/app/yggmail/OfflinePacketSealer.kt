package com.ramka.domain.util

import com.ramka.domain.model.Contact

/**
 * Шов для офлайн-доставки (store-and-forward, например Yggmail): запечатывает пакет так, чтобы его мог прочитать
 * ТОЛЬКО получатель, и вскрывает пришедшие запечатанные пакеты с проверкой отправителя.
 *
 * ПОЧЕМУ ЭТО ОТДЕЛЬНЫЙ ШОВ. Прямые каналы (LAN, Yggdrasil, relay) шифруют сами: Noise IK по живому TCP-соединению,
 * где обе стороны онлайн и обмениваются тремя сообщениями рукопожатия. Для письма это невозможно: получатель
 * офлайн, ответа ждать нельзя. Поэтому готового «зашифрованного для офлайна» пакета в проекте нет, а отправлять
 * по почте открытый пакет нельзя. Рабочая схема — односторонний «запечатанный» пакет по мотивам Noise_X
 * (`com.ramka.crypto.offline.OfflineSeal`, DEVIATIONS.md O-1…O-6); интерфейс остаётся в домене, чтобы доменный код
 * не зависел от криптомодуля. [UnavailableOfflineSealer] — безопасная заглушка «схемы нет» для тестов и сборок без неё:
 * канал при этом ничего не отправляет и не принимает.
 */
interface OfflinePacketSealer {
    /** Есть ли рабочая схема. false — офлайн-каналы отключены целиком (fail-closed). */
    val isAvailable: Boolean

    /** Запечатывает [plaintext] для [recipient]; null — запечатать нельзя, отправлять нельзя. */
    suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray?

    /**
     * Вскрывает пакет. @return пара (публичный X25519-ключ аутентифицированного отправителя, открытый текст)
     * в том же виде, что у прямых каналов, либо null, если пакет не вскрылся или отправитель не подтверждён.
     */
    suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>?
}

/** Заглушка по умолчанию: ничего не запечатывает и не вскрывает. Открытый текст по почте не уходит никогда. */
object UnavailableOfflineSealer : OfflinePacketSealer {
    override val isAvailable: Boolean = false
    override suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray? = null
    override suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>? = null
}
