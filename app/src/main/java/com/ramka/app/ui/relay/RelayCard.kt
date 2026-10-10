package com.ramka.app.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramka.app.ui.settings.failureText

/** Карточка релея в списке: метка/адрес, бейдж статуса и кнопки изменить / вверх / вниз / удалить. */
@Composable
fun RelayCard(
    item: RelayListItem,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.entry
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (entry.label.isNotBlank()) {
                        Text(
                            entry.address.format(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                RelayStatusBadge(item.status, Modifier.padding(end = 8.dp))
            }
            val status = item.status
            if (status is RelayBadgeState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(
                    failureText(status.failure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (entry.issue != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Запись неполная и не используется: откройте её и заполните поля.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Изменить") }
                IconButton(onClick = onMoveUp, enabled = item.canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Вверх")
                }
                IconButton(onClick = onMoveDown, enabled = item.canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз")
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить") }
            }
        }
    }
}
