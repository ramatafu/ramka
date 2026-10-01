package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.background.OemSettingsLauncher

/**
 * Настройки (ЭТАП B.8): «Фоновая доставка» (по умолчанию ВКЛ, управляет WorkManager) и
 * под-тумблер «Постоянный сервис для надёжности» (по умолчанию ВЫКЛ, foreground-сервис).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            SettingSwitchRow(
                title = "Фоновая доставка",
                description = "Периодически (не чаще раза в 15 минут) пытаться доставить ожидающие сообщения.",
                checked = viewModel.backgroundDelivery,
                enabled = true,
                onCheckedChange = viewModel::onBackgroundDeliveryChanged
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Постоянный сервис для надёжности",
                description = "Держит приложение живым при жёстких ограничениях батареи. Показывает постоянное " +
                    "уведомление «Фоновая доставка активна», расходует больше заряда.",
                checked = viewModel.persistentService,
                enabled = viewModel.backgroundDelivery,
                onCheckedChange = viewModel::onPersistentServiceChanged
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Автообнаружение в сети (mDNS)",
                description = "Другие устройства ramka находят вас автоматически. Если выключить, вас " +
                    "видно только по IP и порту из QR-приглашения, а доставка идёт по расписанию и " +
                    "ручному обновлению.",
                checked = viewModel.mdnsEnabled,
                enabled = true,
                onCheckedChange = viewModel::onMdnsChanged
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = viewModel::openHintManually) {
                Text("Как разрешить фоновую работу")
            }
        }
    }

    if (viewModel.showHint) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHint,
            title = { Text("Фоновая работа") },
            text = { Text(viewModel.hintText) },
            confirmButton = {
                TextButton(onClick = {
                    OemSettingsLauncher.open(context, viewModel.oem)
                    viewModel.dismissHint()
                }) { Text("Открыть настройки") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissHint) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
