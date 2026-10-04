package com.ramka.domain.relay

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeClock(var now: Long = 10_000L) : RelayClock {
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

internal class FakePoolSource(initial: RelayPool) : RelayPoolSource {
    val state = MutableStateFlow(initial)
    override val pool: StateFlow<RelayPool> = state
}

internal const val POOL_TEST_TOKEN = "test-token-0123456789abcdef"

internal fun poolEntry(id: String, token: String = POOL_TEST_TOKEN, pin: String? = null, label: String = "") =
    RelayEntry(id, RelayAddress("$id.example.com", 48766), token, pin, label)

internal fun poolOf(vararg ids: String, enabled: Boolean = true, batterySaver: Boolean = false) =
    RelayPool(enabled = enabled, batterySaver = batterySaver, entries = ids.map { poolEntry(it) })

internal fun addr(id: String) = RelayAddress("$id.example.com", 48766)

internal fun testContact(id: String = "c1") = Contact(
    localId = id, alias = "a", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
    lastKnownAddress = null, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0
)
