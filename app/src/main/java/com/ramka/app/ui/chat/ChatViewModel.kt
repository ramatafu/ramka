package com.ramka.app.ui.chat

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.files.AndroidIncomingFileStore
import com.ramka.domain.model.Contact
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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

    fun clearDecryptedFileCache() {
        viewModelScope.launch(Dispatchers.IO) {
            incomingFileStore.clearDecryptedCache()
        }
    }
}