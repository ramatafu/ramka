package com.ramka.app.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.data.TurnServerEntry

/** Список TURN-серверов: карточки с переключателем, перестановкой и удалением; внизу — «Добавить». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnServerListScreen(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenTurnGuide: () -> Unit,
    viewModel: TurnServerListViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<TurnServerEntry?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TURN-серверы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenTurnGuide) {
                        Text(
                            "!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !loaded -> Unit
                    servers.isEmpty() -> Text(
                        "Добавьте TURN-сервер для работы звонков через мобильный интернет. Без TURN-сервера " +
                            "звонки работают только в локальной сети (Wi-Fi) или между устройствами с прямым соединением.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(servers, key = { _, server -> server.id }) { index, server ->
                            TurnServerCard(
                                server = server,
                                canMoveUp = index > 0,
                                canMoveDown = index < servers.lastIndex,
                                onEdit = { onEditServer(server.id) },
                                onToggle = { viewModel.toggleEnabled(server.id) },
                                onMoveUp = { viewModel.moveUp(server.id) },
                                onMoveDown = { viewModel.moveDown(server.id) },
                                onDelete = { pendingDelete = server }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddServer, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить TURN-сервер")
            }
        }
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить TURN-сервер?") },
            text = { Text("«${server.url}» будет удалён из списка вместе с паролем.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeServer(server.id)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun TurnServerCard(
    server: TurnServerEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(server.url, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        server.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = server.isEnabled, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Вверх")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}