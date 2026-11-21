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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Экран «Общие настройки»: фоновая доставка, постоянный сервис, автообнаружение в сети (mDNS) и ссылка на инструкцию
 * по разрешению фоновой работы. Логика сохранения в [SettingsViewModel] не менялась.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Общие настройки") },
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
            SettingsCard {
                SettingSwitchRow(
                    title = "Фоновая доставка",
                    description = "Периодически (не чаще раза в 15 минут) пытаться доставить ожидающие сообщения.",
                    checked = viewModel.backgroundDelivery,
                    enabled = true,
                    onCheckedChange = viewModel::onBackgroundDeliveryChanged
                )
            }
            SettingsCard {
                SettingSwitchRow(
                    title = "Постоянный сервис для надёжности",
                    description = "Держит приложение живым при жёстких ограничениях батареи. Показывает постоянное " +
                        "уведомление «Фоновая доставка активна», расходует больше заряда.",
                    checked = viewModel.persistentService,
                    enabled = viewModel.backgroundDelivery,
                    onCheckedChange = viewModel::onPersistentServiceChanged
                )
            }
            SettingsCard {
                SettingSwitchRow(
                    title = "Автообнаружение в сети (mDNS)",
                    description = "Другие устройства ramka находят вас автоматически. Если выключить, вас " +
                        "видно только по IP и порту из QR-приглашения, а доставка идёт по расписанию и " +
                        "ручному обновлению.",
                    checked = viewModel.mdnsEnabled,
                    enabled = true,
                    onCheckedChange = viewModel::onMdnsChanged
                )
            }
            TextButton(onClick = viewModel::openHintManually) {
                Text("Как разрешить фоновую работу")
            }
        }
    }

    BackgroundWorkHintDialog(viewModel)
}
