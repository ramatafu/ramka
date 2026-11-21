package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.background.OemSettingsLauncher

/**
 * Главный экран «Настройки»: секции «Общие» и «Звонки» и переходы на «Сеть и серверы», «Настройки прокси»,
 * «О приложении». Всё про сети (релеи, TURN, Yggdrasil, Yggmail, прокси) живёт на экране «Сеть и серверы».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNetworkServers: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(icon = Icons.Filled.Settings, title = "Общие") {
                SettingSwitchRow(
                    title = "Фоновая доставка",
                    description = "Периодически (не чаще раза в 15 минут) пытаться доставить ожидающие сообщения.",
                    checked = viewModel.backgroundDelivery,
                    enabled = true,
                    onCheckedChange = viewModel::onBackgroundDeliveryChanged
                )
                SettingSwitchRow(
                    title = "Постоянный сервис для надёжности",
                    description = "Держит приложение живым при жёстких ограничениях батареи. Показывает постоянное " +
                        "уведомление «Фоновая доставка активна», расходует больше заряда.",
                    checked = viewModel.persistentService,
                    enabled = viewModel.backgroundDelivery,
                    onCheckedChange = viewModel::onPersistentServiceChanged
                )
                SettingSwitchRow(
                    title = "Автообнаружение в сети (mDNS)",
                    description = "Другие устройства ramka находят вас автоматически. Если выключить, вас " +
                        "видно только по IP и порту из QR-приглашения, а доставка идёт по расписанию и " +
                        "ручному обновлению.",
                    checked = viewModel.mdnsEnabled,
                    enabled = true,
                    onCheckedChange = viewModel::onMdnsChanged
                )
                TextButton(onClick = viewModel::openHintManually) {
                    Text("Как разрешить фоновую работу")
                }
            }

            SettingsSection(icon = Icons.Filled.Call, title = "Звонки") {
                SettingSwitchRow(
                    title = "Звук при входящем звонке",
                    description = "Играть рингтон, пока звонит собеседник.",
                    checked = viewModel.callRingtone,
                    enabled = true,
                    onCheckedChange = viewModel::onCallRingtoneChanged
                )
                SettingSwitchRow(
                    title = "Вибрация при входящем звонке",
                    description = "Вибрировать, пока звонит собеседник, в том числе в беззвучном режиме.",
                    checked = viewModel.callVibration,
                    enabled = true,
                    onCheckedChange = viewModel::onCallVibrationChanged
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenNetworkServers, modifier = Modifier.fillMaxWidth()) {
                    Text("Сеть и серверы")
                }
                OutlinedButton(onClick = onOpenProxySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Настройки прокси")
                }
                OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("О приложении")
                }
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
