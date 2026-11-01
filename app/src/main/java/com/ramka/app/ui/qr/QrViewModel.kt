package com.ramka.app.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.crypto.keys.KeyFingerprint
import com.ramka.crypto.keys.KeyManager
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.usecase.AddContactUseCase
import com.ramka.domain.usecase.Invite
import com.ramka.domain.util.ContactNetworkAddress
import com.ramka.domain.util.YggdrasilAddress
import com.ramka.network.local.LanAddressUtil
import com.ramka.network.protocol.InviteCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LAN_PORT = 48765

@HiltViewModel
class QrViewModel @Inject constructor(
    private val keyManager: KeyManager,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val addContactUseCase = AddContactUseCase(contactRepository)

    private val _myInviteText = MutableStateFlow<String?>(null)
    val myInviteText: StateFlow<String?> = _myInviteText.asStateFlow()

    private val _pendingVerification = MutableStateFlow<PendingVerification?>(null)
    val pendingVerification: StateFlow<PendingVerification?> = _pendingVerification.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun generateMyInvite() {
        val identity = keyManager.getOrCreateIdentity()
        val host = LanAddressUtil.getLocalIpv4Address()
        _myInviteText.value = InviteCodec.createForSelf(identity.x25519Public, identity.ed25519Public, host, LAN_PORT)
    }

    /** Вызывается сканером после успешного распознавания QR. */
    fun onQrScanned(qrText: String) {
        val invite = runCatching { InviteCodec.parse(qrText) }.getOrNull()
        if (invite == null) {
            _error.value = "Не удалось распознать приглашение"
            return
        }
        if (invite.isExpired()) {
            _error.value = "Срок действия приглашения истёк"
            return
        }
        // Экран верификации ключа перед сохранением контакта — п. 8.4: показываем
        // отпечаток пары ключей, чтобы пользователь мог свериться "из рук в руки".
        val fingerprint = KeyFingerprint.compute(invite.publicKey, invite.signingPublicKey)
        _pendingVerification.value = PendingVerification(invite, fingerprint)
    }

    /** [yggmailAddress] необязателен; невалидный (не из диапазона Yggdrasil) молча не сохраняется — поле в диалоге это подсвечивает. */
    fun confirmAddContact(alias: String, yggmailAddress: String? = null) {
        val invite = _pendingVerification.value?.invite ?: return
        val ygg = yggmailAddress?.trim()?.takeIf { it.isNotEmpty() && ContactNetworkAddress.isValid(it) }
        viewModelScope.launch {
            addContactUseCase(invite, alias.ifBlank { "Новый контакт" }, ygg)
            _pendingVerification.value = null
        }
    }

    fun cancelVerification() {
        _pendingVerification.value = null
    }

    fun consumeError() {
        _error.value = null
    }
}

data class PendingVerification(val invite: Invite, val fingerprint: String)
