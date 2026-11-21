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
import androidx.hilt.navigation.compose.hiltViewModel

/** Экран «Звонки»: звук, вибрация и настройки P2P при входящем звонке. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Звонки") },
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
                    title = "Звук при входящем звонке",
                    description = "Играть рингтон, пока звонит собеседник.",
                    checked = viewModel.callRingtone,
                    enabled = true,
                    onCheckedChange = viewModel::onCallRingtoneChanged
                )
            }
            SettingsCard {
                SettingSwitchRow(
                    title = "Вибрация при входящем звонке",
                    description = "Вибрировать, пока звонит собеседник, в том числе в беззвучном режиме.",
                    checked = viewModel.callVibration,
                    enabled = true,
                    onCheckedChange = viewModel::onCallVibrationChanged
                )
            }
            SettingsCard {
                SettingSwitchRow(
                    title = "Прямое P2P-соединение",
                    description = "Звонки в одной сети идут напрямую. Раскрывает ваш IP собеседнику. Без него звонок идёт только через TURN-сервер.",
                    checked = viewModel.allowDirectP2p,
                    enabled = true,
                    onCheckedChange = viewModel::onAllowDirectP2pChanged
                )
            }
        }
    }

    // Разовая подсказка про фоновую работу создаётся вместе с ViewModel: показываем её и здесь, чтобы не потерять.
    BackgroundWorkHintDialog(viewModel)
}