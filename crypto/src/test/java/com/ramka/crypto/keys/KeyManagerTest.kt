package com.ramka.crypto.keys

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyManagerTest {

    @Test
    fun `getOrCreateIdentity is idempotent — repeated calls return identical public keys`() {
        val keyManager = KeyManager(InMemoryKeyValueStore())

        val first = keyManager.getOrCreateIdentity()
        val second = keyManager.getOrCreateIdentity()

        assertArrayEquals(first.x25519Public, second.x25519Public)
        assertArrayEquals(first.ed25519Public, second.ed25519Public)
    }

    @Test
    fun `signWithIdentity produces signatures verifiable after several repeated key loads`() {
        val keyManager = KeyManager(InMemoryKeyValueStore())
        val identity = keyManager.getOrCreateIdentity()

        // Каждый вызов signWithIdentity внутри себя заново читает приватный
        // Ed25519-ключ из хранилища (loadEd25519Private()) — несколько вызовов
        // подряд воспроизводят ровно ту последовательность повторных чтений
        // одного и того же ключа, на которой ловился баг ссылочного возврата
        // в InMemoryKeyValueStore (см. KDoc KeyValueStore и её историю правок).
        val signature1 = keyManager.signWithIdentity("first".toByteArray())
        val signature2 = keyManager.signWithIdentity("second".toByteArray())
        val signature3 = keyManager.signWithIdentity("third".toByteArray())

        assertTrue(Ed25519Verification.verify(identity.ed25519Public, "first".toByteArray(), signature1))
        assertTrue(Ed25519Verification.verify(identity.ed25519Public, "second".toByteArray(), signature2))
        assertTrue(Ed25519Verification.verify(identity.ed25519Public, "third".toByteArray(), signature3))
    }

    /**
     * КОНТРОЛЬНОЕ СВОЙСТВО. [BrokenKeyValueStore] ниже — намеренная копия СТАРОГО,
     * ошибочного поведения `InMemoryKeyValueStore` (getBytes/putBytes без
     * `.copyOf()`, то есть по ссылке). Этот тест доказывает, что тесты выше
     * (`getOrCreateIdentity is idempotent`, `signWithIdentity ... several loads`)
     * действительно способны поймать данный класс ошибок, а не проходят
     * независимо от того, исправлено хранилище или нет: на сломанной реализации
     * повторные чтения одного и того же identity-ключа обязаны разойтись.
     *
     * Механизм: `KeyManager.loadX25519Private()`/`loadEd25519Private()` в ветке
     * "ключ уже есть в хранилище" делают `bytes.fill(0)` над результатом
     * `getBytes()` (гигиена — временный byte[] большене нужен). Если `getBytes`
     * вернул живую ссылку на внутренний массив хранилища (баг), это `fill(0)`
     * зануляет сами хранимые байты. `getOrCreateIdentity()` при этом читает
     * хранилище на КАЖДЫЙ вызов (не кэширует), поэтому уже 2–3 повторных вызова
     * на сломанном хранилище дают разные публичные ключи вместо одного и того же.
     */
    @Test
    fun `control — the pre-fix reference-returning store lets repeated identity loads diverge`() {
        val keyManager = KeyManager(BrokenKeyValueStore())

        val observedX25519Keys = (1..5)
            .map { keyManager.getOrCreateIdentity().x25519Public.toHexString() }
            .toSet()

        assertTrue(
            "Сломанное (ссылочное) хранилище должно было породить расхождение между " +
                "повторными вызовами getOrCreateIdentity(), но все 5 результатов совпали — " +
                "либо баг больше не воспроизводится так, либо BrokenKeyValueStore здесь " +
                "перестал точно копировать старое поведение.",
            observedX25519Keys.size > 1
        )
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    /** Точная копия ДО-фикса поведения InMemoryKeyValueStore — используется только этим тестом. */
    private class BrokenKeyValueStore : KeyValueStore {
        private val map = mutableMapOf<String, ByteArray>()
        override fun getBytes(key: String): ByteArray? = map[key] // БАГ: без .copyOf()
        override fun putBytes(key: String, value: ByteArray) { map[key] = value } // БАГ: без .copyOf()
    }
}
