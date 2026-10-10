package com.ramka.app.ui.chat

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    fileTracker: FileTransferTracker
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val attemptDelivery = AttemptDeliveryUseCase(contactRepository, transportRepository)

    private val sendMessageUseCase = SendMessageUseCase(
        contactRepository, messageRepository, outboxRepository, attemptDelivery
    )

    val contact: StateFlow<Contact?> = flow { emit(contactRepository.getContact(contactId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Сообщения из БД; у файловых с активной передачей прогресс подменяется живым значением из
     * [FileTransferTracker] (в БД прогресс пишется только в конце). Без активных передач список
     * идёт ровно как из БД — текстовые сообщения работают как раньше.
     */
    val messages: StateFlow<List<Message>> = combine(
        messageRepository.observeMessages(contactId),
        fileTracker.progress
    ) { stored, live ->
        if (live.isEmpty()) {
            stored
        } else {
            stored.map { message ->
                val body = message.body
                val percent = live[message.localId]
                if (body is MessageBody.File && percent != null) message.copy(body = body.copy(progress = percent)) else message
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)

    /** Одноразовые сообщения для Snackbar. */
    val events: Flow<String> = _events.receiveAsFlow()

    var draftText by mutableStateOf("")
        private set

    fun onDraftChanged(text: String) {
        draftText = text
    }

    fun sendDraft() {
        val text = draftText.trim()
        if (text.isEmpty()) return
        draftText = ""
        viewModelScope.launch {
            sendMessageUseCase(contactId, text)
        }
    }

    /**
     * Пользователь выбрал файл: копируем во внутреннее хранилище (в фоне), сохраняем исходящее
     * файловое сообщение в БД и запускаем передачу в скоупе приложения (не оборвётся при уходе
     * с экрана). Прогресс идёт через [FileTransferTracker], сбой — статус FAILED и «Повторить».
     */
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val prepared = try {
                fileImporter.import(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                _events.send("Файл слишком большой (максимум ${FileTransferConfig.MAX_FILE_SIZE_BYTES / (1024 * 1024)} МБ)")
                return@launch
            } catch (e: EmptyFileException) {
                _events.send("Файл пуст")
                return@launch
            } catch (e: Exception) {
                // IOException, SecurityException и сбои провайдера — одинаково «не удалось».
                _events.send("Не удалось подготовить файл")
                return@launch
            }
            val message = try {
                sendFile.createOutgoing(contactId, prepared.displayName, prepared.sizeBytes, prepared.mimeType, prepared.localUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send("Не удалось подготовить файл")
                return@launch
            }
            fileTransfers.start(message.localId)
        }
    }

    /** Кнопка «Повторить» у файла в статусе FAILED: передача заново с начала. */
    fun retryFile(messageLocalId: String) {
        fileTransfers.start(messageLocalId)
    }

    /**
     * Чат стал виден (экран открыт и приложение на переднем плане, вызывается из ChatScreen
     * по ON_START): помечаем его активным, сбрасываем счётчик непрочитанных и шлём READ-ACK.
     * Сообщения, пришедшие пока чат виден, в счётчик не попадают (см. HandleIncomingTextUseCase).
     */
    fun onChatShown() {
        activeChat.onChatShown(contactId)
        viewModelScope.launch { contactRepository.resetUnreadCount(contactId) }

        // READ-ACK (ЭТАП B.5, ревью п.4): батч одним сообщением со списком ID. Флаг readAckSent
        // проставляется ДО отложенной (джиттер) отправки — см. SendReadAckUseCase.
        viewModelScope.launch { sendReadAck(contactId) }
    }

    /**
     * Чат скрыт (ON_STOP или уход с экрана). Счётчик обнуляется и здесь, при выходе, — страховка
     * на случай, если сообщение проскочило в счётчик, пока чат был открыт (например, в момент
     * открытия, до того как чат помечен активным). Всё, что пришло за время, пока экран был
     * виден, пользователь уже видел. Пометка «скрыт» ставится ПОСЛЕ сброса, чтобы сообщение,
     * пришедшее ровно в этот момент, не было тут же обнулено.
     */
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

    // Приём и расшифровка входящих пакетов обрабатываются один раз на всё приложение
    // в IncomingMessageProcessor (см. data-слой), а не в каждом ChatViewModel —
    // этот экран лишь подписан на уже сохранённые сообщения через observeMessages().
}
