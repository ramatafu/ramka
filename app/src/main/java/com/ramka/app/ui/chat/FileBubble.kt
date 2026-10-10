package com.ramka.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageStatus
import java.util.Locale

/**
 * Содержимое пузыря файлового сообщения: иконка, имя (одна строка, обрезается
 * многоточием), размер со статусом передачи, индикатор прогресса пока идёт передача и
 * кнопка «Повторить» у исходящего в статусе FAILED.
 *
 * Иконка — текстовый глиф 📄: Icons.Filled.InsertDriveFile лежит в
 * material-icons-extended, которую без отдельного одобрения не подключаем
 * (см. примечание в ContactsScreen.kt). Время и статус рисует MessageBubble.
 */
@Composable
internal fun FileBubble(
    body: MessageBody.File,
    isOutgoing: Boolean,
    status: MessageStatus,
    onRetry: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 160.dp)
            .clickable(enabled = isFileOpenable(body, isOutgoing, status), onClick = onOpen)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📄", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = body.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = fileStatusLine(body, isOutgoing, status),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == MessageStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (status == MessageStatus.SENDING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { body.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = RamkaAccent
            )
        }
        if (isOutgoing && status == MessageStatus.FAILED) {
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

/**
 * Нажатие открывает файл, только если он есть на устройстве: исходящий — пока есть локальная копия
 * (передача может ещё идти), входящий — когда принят целиком (DELIVERED/READ) и сохранён.
 */
internal fun isFileOpenable(body: MessageBody.File, isOutgoing: Boolean, status: MessageStatus): Boolean =
    body.uri != null && (isOutgoing || status == MessageStatus.DELIVERED || status == MessageStatus.READ)

/**
 * Вторая строка файлового пузыря: размер и состояние передачи.
 * Исходящий: «Отправка… 40%» / «Ошибка отправки» / только размер (отправлено, ✓ рисует MessageBubble).
 * Входящий: «Загрузка… 40%» / «Ошибка загрузки» / «Получено».
 */
internal fun fileStatusLine(body: MessageBody.File, isOutgoing: Boolean, status: MessageStatus, locale: Locale = Locale.getDefault()): String {
    val size = formatFileSize(body.fileSize, locale)
    val percent = body.progress.coerceIn(0, 100)
    val state = when {
        status == MessageStatus.SENDING -> if (isOutgoing) "Отправка… $percent%" else "Загрузка… $percent%"
        status == MessageStatus.FAILED -> if (isOutgoing) "Ошибка отправки" else "Ошибка загрузки"
        !isOutgoing -> "Получено"
        else -> null
    }
    return if (state == null) size else "$size · $state"
}

private const val BYTES_IN_KB = 1024L
private const val BYTES_IN_MB = 1024L * 1024L

/**
 * «123 КБ» / «1,5 МБ». До 1 МБ — целые КБ (минимум 1 КБ для непустого файла),
 * от 1 МБ — МБ с одним знаком. Отрицательные значения трактуются как 0.
 */
internal fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val safe = bytes.coerceAtLeast(0L)
    return if (safe < BYTES_IN_MB) {
        val kb = if (safe == 0L) 0L else maxOf(1L, safe / BYTES_IN_KB)
        "$kb КБ"
    } else {
        String.format(locale, "%.1f МБ", safe.toDouble() / BYTES_IN_MB)
    }
}
