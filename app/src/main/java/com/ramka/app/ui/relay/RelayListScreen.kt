package com.ramka.app.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.domain.relay.RelayPool

/** Список релеёв: общий переключатель пула, карточки с перестановкой кнопками, добавление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayListScreen(
    onBack: () -> Unit,
    onAddRelay: () -> Unit,
    onEditRelay: (String) -> Unit,
    onOpenRelayGuide: () -> Unit,
    viewModel: RelayListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<RelayListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Релеи") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRelayGuide) {
                        Text(
                            "!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Использовать пул релеёв", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Доставка через ваши серверы ramka-relay, когда собеседник вне вашей сети. Серверы " +
                            "видят только зашифрованные байты. Порядок в списке — приоритет при отправке.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    state.toggleHint?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::onEnabledChanged,
                    enabled = state.toggleAvailable
                )
            }
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.items.forEach { item ->
                    RelayCard(
                        item = item,
                        onEdit = { onEditRelay(item.entry.id) },
                        onMoveUp = { viewModel.onMoveUp(item.entry.id) },
                        onMoveDown = { viewModel.onMoveDown(item.entry.id) },
                        onDelete = { pendingDelete = item }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(onClick = onAddRelay, enabled = state.canAdd, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить релей")
            }
            if (!state.canAdd) {
                Text(
                    "В списке не больше ${RelayPool.MAX_ENTRIES} релеёв.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить релей?") },
            text = { Text("«${item.entry.displayName}» будет удалён из списка вместе с токеном.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRemove(item.entry.id)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } }
        )
    }
}