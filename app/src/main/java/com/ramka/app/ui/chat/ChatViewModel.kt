package com.ramka.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.files.AndroidIncomingFileStore
import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Contact
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.usecase.AttemptDeliveryUseCase
import com.ramka.domain.usecase.FileTransferManager
import com.ramka.domain.usecase.SendFileUseCase
import com.ramka.domain.util.FileTransferConfig
import com.ramka.domain.util.FileTransferTracker
import com.ramka.domain.usecase.SendReadAckUseCase
import com.ramka.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val transportRepository: TransportRepository,
    private val activeChat: com.ramka.domain.util.ActiveChatTracker,
    private val sendReadAck: SendReadAckUseCase,
    private val fileImporter: PickedFileImporter,
    private val sendFile: SendFileUseCase,
    private val fileTransfers: FileTransferManager,
    fileTracker: FileTransferTracker,
    private val incomingFileStore: AndroidIncomingFileStore
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])
    private val attemptDelivery = AttemptDeliveryUseCase(contactRepository, transportRepository)
    private val sendMessageUseCase = SendMessageUseCase(contactRepository, messageRepository, outboxRepository, attemptDelivery)

    val contact: StateFlow<Contact?> = flow { emit(contactRepository.getContact(contactId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = combine(
        messageRepository.observeMessages(contactId),
        fileTracker.progress
    ) { stored, live ->
        stored.map { message ->
            val body = message.body
            val percent = live[message.localId]
            if (body is MessageBody.File && percent != null) message.copy(body = body.copy(progress = percent)) else message
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Контакты для окна «Переслать» — все, кроме текущего собеседника. */
    val forwardTargets: StateFlow<List<Contact>> = contactRepository.observeContacts()
        .map { list -> list.filter { it.localId != contactId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    // ИСПРАВЛЕНО: Channel вместо SharedFlow
    private val _openFileRequest = Channel<OpenFileRequest>(Channel.BUFFERED)
    val openFileRequest: Flow<OpenFileRequest> = _openFileRequest.receiveAsFlow()

    var draftText by mutableStateOf("")
        private set

    fun onDraftChanged(text: String) { draftText = text }

    fun sendDraft() {
        val text = draftText.trim()
        if (text.isEmpty()) return
        draftText = ""
        viewModelScope.launch { sendMessageUseCase(contactId, text) }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val prepared = try {
                fileImporter.import(uri)
            } catch (e: CancellationException) { throw e }
            catch (e: FileTooLargeException) {
                _events.trySend("Файл слишком большой (максимум ${FileTransferConfig.MAX_FILE_SIZE_BYTES / (1024 * 1024)} МБ)")
                return@launch
            } catch (e: EmptyFileException) {
                _events.trySend("Файл пуст")
                return@launch
            } catch (e: Exception) {
                _events.trySend("Не удалось подготовить файл")
                return@launch
            }
            val message = try {
                sendFile.createOutgoing(contactId, prepared.displayName, prepared.sizeBytes, prepared.mimeType, prepared.localUri)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _events.trySend("Не удалось подготовить файл")
                return@launch
            }
            fileTransfers.start(message.localId)
        }
    }

    fun retryFile(messageLocalId: String) { fileTransfers.start(messageLocalId) }

    fun onChatShown() {
        activeChat.onChatShown(contactId)
        viewModelScope.launch { contactRepository.resetUnreadCount(contactId) }
        viewModelScope.launch { sendReadAck(contactId) }
    }

    fun onChatHidden() {
        viewModelScope.launch {
            contactRepository.resetUnreadCount(contactId)
            activeChat.onChatHidden(contactId)
        }
    }

    override fun onCleared() {
        activeChat.onChatHidden(contactId)
        super.onCleared()
    }

    fun onFileClicked(uriString: String, fileName: String, mimeType: String?) {
        Log.d("ChatViewModel", "🖱️ onFileClicked: uri='$uriString', name='$fileName', mime='$mimeType'")

        viewModelScope.launch(Dispatchers.IO) {
            if (AndroidIncomingFileStore.isEncryptedUri(uriString)) {
                val transferId = AndroidIncomingFileStore.transferIdFrom(uriString)
                if (transferId != null) {
                    val decryptedUriString = incomingFileStore.getDecryptedFileUri(transferId, fileName)
                    Log.d("ChatViewModel", "🖱️ Результат getDecryptedFileUri (String): $decryptedUriString")

                    withContext(Dispatchers.Main) {
                        if (decryptedUriString != null) {
                            // ИСПРАВЛЕНО: trySend вместо tryEmit
                            _openFileRequest.trySend(OpenFileRequest(Uri.parse(decryptedUriString), mimeType, fileName))
                        } else {
                            _events.trySend("Не удалось открыть файл: ошибка расшифровки")
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _openFileRequest.trySend(OpenFileRequest(Uri.parse(uriString), mimeType, fileName))
                }
            }
        }
    }

    fun onSaveFileClicked(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (AndroidIncomingFileStore.isEncryptedUri(uriString)) {
                val transferId = AndroidIncomingFileStore.transferIdFrom(uriString)
                if (transferId != null) {
                    val success = incomingFileStore.exportToPublicStorage(transferId, "file", "*/*") != null
                    withContext(Dispatchers.Main) {
                        if (success) _events.trySend("Файл сохранён")
                        else _events.trySend("Не удалось сохранить файл")
                    }
                }
            }
        }
    }

    // ---------------- Контекстное меню сообщения ----------------

    /** 📋 Копировать: только текст. Содержимое помечено как чувствительное (Android 13+ не показывает его в превью буфера). */
    fun copyMessage(message: Message) {
        val text = (message.body as? MessageBody.Text)?.text ?: return
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("message", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        try {
            manager.setPrimaryClip(clip)
        } catch (e: Exception) {
            _events.trySend("Не удалось скопировать")
            return
        }
        // С Android 13 система сама показывает подтверждение копирования — свой текст был бы дублем.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) _events.trySend("Скопировано")
    }

    /**
     * ↗️ Переслать: текст уходит выбранному контакту как НОВОЕ обычное сообщение (через Outbox, с доставкой
     * и ACK, как любое другое). Файлы пока не пересылаются: принятый файл хранится зашифрованным, и
     * пересылка потребует отдельного шага.
     */
    fun forwardMessage(message: Message, targetContactId: String) {
        val text = (message.body as? MessageBody.Text)?.text
        if (text == null) {
            _events.trySend("Пересылка файлов пока недоступна")
            return
        }
        viewModelScope.launch {
            try {
                sendMessageUseCase(targetContactId, text)
                _events.trySend("Переслано")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend("Не удалось переслать")
            }
        }
    }

    /** 🗑️ Удалить у меня: запись, очередь отправки и файл. Собеседник ничего не узнаёт. */
    fun deleteMessageLocally(messageLocalId: String) {
        viewModelScope.launch {
            val message = messageRepository.getMessage(messageLocalId)
            if (message == null || message.contactId != contactId) return@launch
            removeLocally(message)
        }
    }

    /**
     * 🗑️ Удалить у всех — только для СВОИХ сообщений. Собеседнику уходит [AppMessage.DeleteMessage], и ЛИШЬ
     * после успешной отправки сообщение удаляется у нас. Если собеседник недоступен, сообщение остаётся —
     * иначе оно осталось бы у него без нашего ведома; можно повторить позже или удалить «у меня».
     * Текст, который ещё не ушёл (SENDING/FAILED), просто снимается с очереди — сети не нужно.
     */
    fun deleteMessageForEveryone(messageLocalId: String) {
        viewModelScope.launch {
            val message = messageRepository.getMessage(messageLocalId)
            if (message == null || message.contactId != contactId) return@launch
            if (message.direction != MessageDirection.OUTGOING) {
                _events.trySend("У всех можно удалить только свои сообщения")
                return@launch
            }
            if (message.body is MessageBody.File && message.status == MessageStatus.SENDING) {
                _events.trySend("Дождитесь окончания передачи файла")
                return@launch
            }
            val neverSent = message.body is MessageBody.Text &&
                (message.status == MessageStatus.SENDING || message.status == MessageStatus.FAILED)
            if (!neverSent) {
                val sent = try {
                    attemptDelivery(contactId, AppMessage.DeleteMessage(message.localId).encode())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (!sent) {
                    _events.trySend("Собеседник недоступен — сообщение не удалено. Попробуйте позже")
                    return@launch
                }
            }
            if (removeLocally(message) && !neverSent) _events.trySend("Удалено у всех")
        }
    }

    /** ℹ️ Информация. Точных времён доставки и прочтения нет по дизайну (см. [MessageInfo]). */
    fun getMessageInfo(message: Message): MessageInfo = message.toInfo()

    private suspend fun removeLocally(message: Message): Boolean {
        if (message.body is MessageBody.File && message.status == MessageStatus.SENDING) {
            _events.trySend("Дождитесь окончания передачи файла")
            return false
        }
        try {
            outboxRepository.remove(message.localId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // записи в очереди могло не быть
        }
        messageRepository.deleteMessage(message.localId)
        cleanupFileOf(message)
        return true
    }

    /** Стирает файл удалённого файлового сообщения: зашифрованный принятый или исходящую копию в outgoing_files. */
    private suspend fun cleanupFileOf(message: Message) {
        val body = message.body as? MessageBody.File ?: return
        withContext(Dispatchers.IO) {
            val uri = body.uri ?: return@withContext
            val transferId = AndroidIncomingFileStore.transferIdFrom(uri)
            if (transferId != null) {
                try { incomingFileStore.deleteStoredFile(transferId) } catch (_: Exception) { }
                return@withContext
            }
            if (uri.startsWith("file:")) {
                try {
                    val file = File(Uri.parse(uri).path ?: return@withContext).canonicalFile
                    val outgoingDir = File(appContext.filesDir, OUTGOING_DIR).canonicalFile
                    if (file.parentFile == outgoingDir) file.delete()
                } catch (_: Exception) { }
            }
        }
    }

    fun clearDecryptedFileCache() {
        viewModelScope.launch(Dispatchers.IO) {
            incomingFileStore.clearDecryptedCache()
        }
    }
}