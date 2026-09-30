package com.ramka.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.domain.model.Contact
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.usecase.ProcessOutboxUseCase
import com.ramka.app.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    contactRepository: ContactRepository,
    private val processOutboxUseCase: ProcessOutboxUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val contacts: StateFlow<List<Contact>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Триггер 3/3 из ЭТАП B.2 — ручное «обновить» из списка контактов. */
    fun refreshOutbox() {
        viewModelScope.launch {
            processOutboxUseCase.processDue(System.currentTimeMillis())
        }
    }

    /** ЭТАП B.7/8 — запрашивать POST_NOTIFICATIONS автоматически не более одного раза. */
    val hasAskedNotificationPermissionBefore: Boolean get() = appPreferences.hasAskedNotificationPermission

    fun markNotificationPermissionAsked() {
        appPreferences.hasAskedNotificationPermission = true
    }
}
