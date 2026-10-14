package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkServersScreen(
    onBack: () -> Unit,
    onOpenRelays: () -> Unit,
    onOpenTurnServers: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val relayPool by viewModel.relayPool.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сеть и серверы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedButton(onClick = onOpenRelays, modifier = Modifier.fillMaxWidth()) {
                Text("Релеи: ${relayPool.entries.size}")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenTurnServers, modifier = Modifier.fillMaxWidth()) {
                Text("TURN-серверы")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSpeedTest, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить скорость интернета")
            }
        }
    }
}