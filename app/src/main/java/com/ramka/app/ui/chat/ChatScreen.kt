package com.ramka.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.app.ui.theme.RamkaBubbleOutgoing
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран «Чат» — см. PROJECT_SPEC.md п. 8.3. Без имени отправителя в 1-на-1, без точного
 * времени статусов. Кнопка вложения — текстовый глиф (📎), т.к. Icons.Filled.AttachFile
 * не входит в material-icons-core — см. примечание в ContactsScreen.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val contact by viewModel.contact.collectAsState()
    val messages by viewModel.messages.collectAsState()

    // Чат "виден" только пока экран на переднем плане (ON_START..ON_STOP): сворачивание
    // приложения снова делает новые сообщения непрочитанными.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onChatShown()
                Lifecycle.Event.ON_STOP -> viewModel.onChatHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onChatHidden()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.alias ?: "…") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
                // Намеренно нет индикатора «онлайн» / «был в сети» — запрет п. 3.5/8.1.
            )
        },
        bottomBar = {
            MessageInputBar(
                text = viewModel.draftText,
                onTextChange = viewModel::onDraftChanged,
                onSend = viewModel::sendDraft
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messages.asReversed(), key = { it.localId }) { message ->
                MessageBubble(message)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val bubbleColor = if (isOutgoing) RamkaBubbleOutgoing else RamkaAccent.copy(alpha = 0.18f)
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = (message.body as? MessageBody.Text)?.text.orEmpty(),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatLocalTime(message.localCreatedAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (isOutgoing) {
                    Spacer(Modifier.width(4.dp))
                    StatusTicks(message.status)
                }
            }
        }
    }
}

/**
 * Три значка по ЭТАП B.4: 🕐 (в очереди или попытка доставки идёт/повторяется),
 * ✓ (доставлено — получен DELIVERED-ACK), ✓✓ (прочитано — получен READ-ACK).
 * Без точного времени доставки/прочтения (п. 3.3, 8.3) — было и остаётся так.
 *
 * SENT (сокет-отправка прошла успешно) сюда же, в 🕐 — это ещё не "доставлено" в
 * смысле UI: получатель мог принять байты по TCP и тут же упасть, не сохранив
 * сообщение. Настоящее подтверждение — только ACK, см. IncomingMessageProcessor
 * и DEVIATIONS.md, раздел «ЭТАП B — решения по Outbox/ACK».
 */
@Composable
private fun StatusTicks(status: MessageStatus) {
    val (text, color) = when (status) {
        MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.FAILED ->
            "🕐" to MaterialTheme.colorScheme.secondary
        MessageStatus.DELIVERED -> "✓" to MaterialTheme.colorScheme.secondary
        MessageStatus.READ -> "✓✓" to RamkaAccent
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun MessageInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    // Scaffold передаёт insets только в padding для content-слота — bottomBar (это и
    // есть MessageInputBar) сам за собой ничего не подкладывает, поэтому без явного
    // navigationBarsPadding() поле ввода наезжает на системные кнопки навигации.
    // imePadding() дополнительно поднимает поле над клавиатурой, когда она открыта
    // (на part устройств высота IME превышает высоту навигационной панели — оба
    // модификатора не конфликтуют, Compose берёт больший из применимых отступов).
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* вложения — этап 6 */ }) {
                Text("📎", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4
            )
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = RamkaAccent)
            }
        }
    }
}

private fun formatLocalTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
