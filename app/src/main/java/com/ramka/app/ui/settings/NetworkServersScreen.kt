package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Экран «Сеть и серверы»: меню из трёх переходов. «Серверы» (релеи, TURN, прокси) и «Yggdrasil» (Yggdrasil и
 * Yggmail) открывают отдельные экраны, «Проверить скорость интернета» открывает существующий тест скорости.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkServersScreen(
    onBack: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenYggdrasil: () -> Unit,
    onOpenSpeedTest: () -> Unit
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavCapsule("Серверы", onOpenServers)
            NavCapsule("Yggdrasil", onOpenYggdrasil)
            NavCapsule("Проверить скорость интернета", onOpenSpeedTest)
        }
    }
}
