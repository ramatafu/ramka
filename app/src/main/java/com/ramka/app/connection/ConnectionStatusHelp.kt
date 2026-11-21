package com.ramka.app.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Строка справки: состояние индикатора, заголовок и пояснение. Цвет берётся у самого индикатора. */
internal data class ConnectionStatusHelpItem(
    val state: ConnectionState,
    val title: String,
    val description: String
)

/** Порядок и тексты справки. Тест проверяет, что здесь есть каждое состояние [ConnectionState]. */
internal val connectionStatusHelpItems: List<ConnectionStatusHelpItem> = listOf(
    ConnectionStatusHelpItem(
        ConnectionState.P2P,
        "Зелёный — Локальная сеть (Wi-Fi)",
        "Прямое P2P-соединение с собеседником в той же сети."
    ),
    ConnectionStatusHelpItem(
        ConnectionState.RELAY_PROXY,
        "Маджента (#FF6EC7) — Через прокси",
        "Соединение с релеем через прокси-сервер. IP скрыт."
    ),
    ConnectionStatusHelpItem(
        ConnectionState.RELAY,
        "Синий — Релей",
        "Прямое зашифрованное соединение через сервер-релей."
    ),
    ConnectionStatusHelpItem(
        ConnectionState.YGGDRASIL,
        "Фиолетовый — Yggdrasil / Yggmail",
        "Соединение через mesh-сеть или резервный почтовый канал."
    ),
    ConnectionStatusHelpItem(
        ConnectionState.TURN,
        "Жёлтый — Только звонки (TURN)",
        "Релей недоступен, доступны только голосовые вызовы."
    ),
    ConnectionStatusHelpItem(
        ConnectionState.OFFLINE,
        "Серый — Офлайн",
        "Нет активного соединения."
    )
)

/**
 * Справка «Состояния подключения»: что означает цвет логотипа ramka. Оформление берётся из темы приложения
 * (тёмная схема, скруглённые углы AlertDialog Material 3), цвета кружков те же, что у самого индикатора
 * ([indicatorColor]), поэтому справка не разойдётся с ним.
 */
@Composable
fun ConnectionStatusHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Состояния подключения") },
        text = {
            // Прокрутка: на маленьком экране и в альбомной ориентации шесть пунктов могут не поместиться.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                connectionStatusHelpItems.forEach { ConnectionStatusHelpRow(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Понятно") } }
    )
}

@Composable
private fun ConnectionStatusHelpRow(item: ConnectionStatusHelpItem) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top
    ) {
        // Кружок декоративный: смысл цвета целиком передаёт заголовок рядом.
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(item.state.indicatorColor())
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Иконка-справка для `actions` верхней панели: по нажатию открывает [ConnectionStatusHelpDialog].
 * Состояние «диалог открыт» переживает поворот экрана.
 */
@Composable
fun ConnectionStatusHelpButton() {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { showHelp = true }) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "Состояния подключения",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showHelp) ConnectionStatusHelpDialog(onDismissRequest = { showHelp = false })
}
