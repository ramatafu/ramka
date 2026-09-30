package com.ramka.crypto.keys

/**
 * In-memory реализация [KeyValueStore] для unit-тестов (pure JVM, без Android).
 * НЕ шифрует ничего и не должна использоваться нигде, кроме тестов — единственная
 * задача: дать [KeyManager] хранилище, не требующее Context/Android Keystore.
 *
 * КОНТРАКТ (см. KeyValueStore): getBytes должен возвращать НОВЫЙ массив на каждый
 * вызов, putBytes должен хранить копию переданного массива, а не ссылку на него.
 * Раньше здесь этого не было — getBytes отдавал внутренний массив по ссылке.
 * KeyManager после сборки *PrivateKeyParameters обнуляет byte[], который получил
 * от getBytes (гигиена ключевого материала, см. KeyManager.kt) — без .copyOf() это
 * обнуление било напрямую по хранилищу: второе и последующие чтения одного и того
 * же ключа (а KeyManager.getOrCreateIdentity()/IkHandshake читают один и тот же
 * identity-ключ по нескольку раз за одно рукопожатие) получали уже частично или
 * полностью занулённые байты. Именно это ломало 2 из 14 тестов.
 */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, ByteArray>()

    override fun getBytes(key: String): ByteArray? = map[key]?.copyOf()

    override fun putBytes(key: String, value: ByteArray) {
        map[key] = value.copyOf()
    }
}
