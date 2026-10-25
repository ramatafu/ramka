package com.ramka.app.ui.contacts

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.discovery.LanVisibilityController
import com.ramka.app.files.AndroidIncomingFileStore
import com.ramka.app.files.ContactPhotoStore
import com.ramka.app.ui.chat.OUTGOING_DIR
import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val photoStore: ContactPhotoStore,
    private val incomingFileStore: AndroidIncomingFileStore,
    private val lanVisibilityController: LanVisibilityController
) : ViewModel() {

    val contacts = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Логика запроса разрешений (из твоего оригинального кода)
    var hasAskedNotificationPermissionBefore = false
        private set

    fun markNotificationPermissionAsked() {
        hasAskedNotificationPermissionBefore = true
    }

    // Состояние для зелёного логотипа
    val isLanPeerDiscovered = lanVisibilityController.activeLanPeer
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** Короткие сообщения для Snackbar (ошибки управления контактами). */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    // ---------------- Управление контактами ----------------

    /** Меняет локальный псевдоним. Пустое имя игнорируется, длина ограничена [MAX_ALIAS_LENGTH]. */
    fun updateAlias(contactId: String, newAlias: String) {
        val alias = newAlias.trim().take(MAX_ALIAS_LENGTH)
        if (alias.isEmpty()) return
        viewModelScope.launch {
            try {
                contactRepository.updateAlias(contactId, alias)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend("Не удалось изменить имя")
            }
        }
    }

    /**
     * Сохраняет фото из галереи локально: картинка копируется во внутреннее хранилище (см. [ContactPhotoStore]),
     * в БД пишется file:// URI, старый файл фото удаляется. Собеседнику фото не передаётся.
     */
    fun updatePhoto(contactId: String, source: Uri) {
        if (contactId == ContactConstants.SELF_CONTACT_ID) return // у «Избранного» всегда ⭐
        viewModelScope.launch {
            try {
                val previous = contactRepository.getContact(contactId)?.photoUri
                val saved = photoStore.import(contactId, source)
                if (saved == null) {
                    _events.trySend("Не удалось загрузить фото")
                    return@launch
                }
                try {
                    contactRepository.updatePhotoUri(contactId, saved)
                } catch (e: CancellationException) {
                    photoStore.delete(saved)
                    throw e
                } catch (e: Exception) {
                    photoStore.delete(saved)
                    _events.trySend("Не удалось сохранить фото")
                    return@launch
                }
                photoStore.delete(previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend("Не удалось сохранить фото")
            }
        }
    }

    /** На устройстве нет приложения, умеющего выбирать изображения. */
    fun onPhotoPickerUnavailable() {
        _events.trySend("Не найдено приложение для выбора фото")
    }

    /**
     * Удаляет контакт целиком: файлы его файловых сообщений, очередь отправки, сами сообщения,
     * запись контакта и файл фото. Контакт удаляется последним — при сбое по пути он остаётся в списке
     * и удаление можно повторить.
     */
    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            try {
                val contact = contactRepository.getContact(contactId)
                if (contact?.isSelf == true || contactId == ContactConstants.SELF_CONTACT_ID) {
                    _events.trySend("«Избранное» нельзя удалить")
                    return@launch
                }
                val messages = messageRepository.observeMessages(contactId).first()
                withContext(Dispatchers.IO) { messages.forEach { cleanupFileOf(it) } }
                try {
                    outboxRepository.observePending().first()
                        .filter { it.contactId == contactId }
                        .forEach { outboxRepository.remove(it.messageLocalId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // очередь и так чистится каскадом при удалении сообщений
                }
                messageRepository.deleteAllForContact(contactId)
                contactRepository.removeContact(contactId)
                photoStore.delete(contact?.photoUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend("Не удалось удалить контакт")
            }
        }
    }

    /** Стирает файл удаляемого файлового сообщения: зашифрованный принятый или исходящую копию. */
    private suspend fun cleanupFileOf(message: Message) {
        val body = message.body as? MessageBody.File ?: return
        val uri = body.uri ?: return
        try {
            val transferId = AndroidIncomingFileStore.transferIdFrom(uri)
            if (transferId != null) {
                incomingFileStore.deleteStoredFile(transferId)
                return
            }
            if (uri.startsWith("file:")) {
                val file = File(Uri.parse(uri).path ?: return).canonicalFile
                val outgoingDir = File(appContext.filesDir, OUTGOING_DIR).canonicalFile
                if (file.parentFile == outgoingDir) file.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // не удалось стереть файл — на удаление сообщений это не влияет
        }
    }

    companion object {
        const val MAX_ALIAS_LENGTH = 40
    }
}
