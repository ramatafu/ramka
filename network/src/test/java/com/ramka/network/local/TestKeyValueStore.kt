package com.ramka.network.local

import com.ramka.crypto.keys.KeyValueStore

/** In-memory [KeyValueStore] для тестов network (pure JVM). Копирует массивы в обе стороны — контракт интерфейса. */
internal class TestKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, ByteArray>()

    override fun getBytes(key: String): ByteArray? = map[key]?.copyOf()

    override fun putBytes(key: String, value: ByteArray) {
        map[key] = value.copyOf()
    }
}
