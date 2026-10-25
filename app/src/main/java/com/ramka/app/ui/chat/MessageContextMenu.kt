package com.ramka.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ramka.domain.model.Contact
import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Состояние открытого меню: для какого сообщения и где нажали (в координатах пузыря сообщения). */
data class MessageContextMenuState(val message: Message, val position: Offset)

enum class MessageAction { Copy, Forward, Delete, Info }

/**
 * Долгое нажатие с координатой. Слушает жест на проходе Initial (родитель раньше детей), поэтому работает и
 * поверх пузыря файла, у которого внутри есть свой clickable. После срабатывания остаток жеста поглощается,
 * чтобы вложенный clickable не открыл файл. Прокрутка списка не затрагивается: при сдвиге дальше touchSlop
 * мы просто перестаём следить и ничего не потребляем.
 */
@Composable
fun Modifier.messageLongPress(onLongPress: (Offset) -> Unit): Modifier {
    val current by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var released = false
            val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null ||
                        change.changedToUpIgnoreConsumed() ||
                        (change.position - down.position).getDistance() > slop
                    ) {
                        released = true
                    }
                }
            } == null
            if (timedOut) {
                current(down.position)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

private data class MenuEntry(val icon: ImageVector, val title: String, val action: MessageAction)

/**
 * Меню рядом с местом нажатия: поверхность темы, углы 12dp, тень 8dp, появление fade + scale за 150 мс.
 * Закрывается по нажатию вне меню и по «Назад» (Popup с focusable = true).
 */
@Composable
fun MessageContextMenu(
    state: MessageContextMenuState,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit
) {
    val isText = state.message.body is MessageBody.Text
    val entries = buildList {
        if (isText) add(MenuEntry(Icons.Filled.ContentCopy, "Копировать", MessageAction.Copy))
        if (isText) add(MenuEntry(Icons.Filled.Share, "Переслать", MessageAction.Forward))
        add(MenuEntry(Icons.Filled.Delete, "Удалить", MessageAction.Delete))
        add(MenuEntry(Icons.Filled.Info, "Информация", MessageAction.Info))
    }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val provider = remember(state.position, marginPx) {
        TapMenuPositionProvider(
            tap = IntOffset(state.position.x.roundToInt(), state.position.y.roundToInt()),
            margin = marginPx
        )
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val onSurface = MaterialTheme.colorScheme.onSurface

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.9f)
        ) {
            // padding 12dp — место под тень, иначе окно Popup её обрежет
            Surface(
                modifier = Modifier.padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = onSurface,
                shadowElevation = 8.dp
            ) {
                Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 200.dp)) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = onSurface.copy(alpha = 0.1f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(entry.action) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(entry.icon, contentDescription = null, tint = onSurface, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Ставит меню в точку касания внутри пузыря; если снизу не хватает места — показывает над точкой. */
private class TapMenuPositionProvider(
    private val tap: IntOffset,
    private val margin: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.left + tap.x)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        var y = anchorBounds.top + tap.y
        if (y + popupContentSize.height > windowSize.height - margin) y -= popupContentSize.height
        y = y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        return IntOffset(x, y)
    }
}

/** Второй уровень «Удалить»: «у меня» всегда, «у всех» — только для своих (исходящих) сообщений. */
@Composable
fun DeleteMessageDialog(
    message: Message,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить сообщение?") },
        text = {
            Column {
                TextButton(onClick = onDeleteForMe) { Text("Удалить у меня") }
                // В «Избранном» «у всех» бессмысленно: собеседника нет, удаление всегда локальное.
                if (message.direction == MessageDirection.OUTGOING && message.contactId != ContactConstants.SELF_CONTACT_ID) {
                    TextButton(onClick = onDeleteForEveryone) { Text("Удалить у всех") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ForwardDialog(
    contacts: List<Contact>,
    onSelect: (Contact) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переслать") },
        text = {
            if (contacts.isEmpty()) {
                Text("Нет других контактов", color = MaterialTheme.colorScheme.secondary)
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(contacts, key = { it.localId }) { contact ->
                        Text(
                            text = contact.alias,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(contact) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun MessageInfoDialog(info: MessageInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Информация") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(if (info.isOutgoing) "Отправлено" else "Получено", formatFullTime(info.createdAtEpochMillis))
                InfoRow("Статус", info.statusLabel)
                info.fileSizeBytes?.let { InfoRow("Размер", formatFileSize(it)) }
                InfoRow("ID", info.localId)
                info.remoteMessageId?.let { InfoRow("ID у отправителя", it) }
                Text(
                    "Точное время доставки и прочтения не хранится — так сделано намеренно, ради приватности.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatFullTime(epochMillis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
