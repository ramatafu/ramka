package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Настройки TURN-сервера: адрес (`turn:IP:порт`), логин, пароль (скрыт, с кнопкой «Показать»),
 * переключатель и «Сохранить». Значения читаются из DataStore при открытии экрана
 * (см. [TurnSettingsViewModel]); звонки подхватывают новые настройки при следующем вызове.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnSettingsScreen(
    onBack: () -> Unit,
    viewModel: TurnSettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    // Поля доступны только при включённом TURN (и после загрузки значений из DataStore).
    val fieldsEnabled = viewModel.loaded && viewModel.isEnabled

    LaunchedEffect(viewModel.message) {
        val text = viewModel.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки TURN-сервера") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "TURN-сервер нужен, чтобы видеозвонки работали через мобильный интернет, когда прямое " +
                    "соединение не пробивается. Сервер видит только зашифрованный трафик. " +
                    "Настройки применяются при следующем звонке.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Использовать TURN-сервер",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = viewModel.isEnabled,
                    onCheckedChange = viewModel::onEnabledChanged,
                    enabled = viewModel.loaded
                )
            }

            OutlinedTextField(
                value = viewModel.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text("Адрес сервера") },
                placeholder = { Text("turn:203.0.113.5:3478") },
                isError = viewModel.urlError != null,
                supportingText = { viewModel.urlError?.let { Text(it) } },
                singleLine = true,
                enabled = fieldsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Логин") },
                isError = viewModel.usernameError != null,
                supportingText = { viewModel.usernameError?.let { Text(it) } },
                singleLine = true,
                enabled = fieldsEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Пароль") },
                isError = viewModel.passwordError != null,
                supportingText = { viewModel.passwordError?.let { Text(it) } },
                singleLine = true,
                enabled = fieldsEnabled,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = fieldsEnabled) {
                        Text(if (passwordVisible) "Скрыть" else "Показать")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Назад") }
                Button(
                    onClick = viewModel::onSave,
                    enabled = viewModel.loaded && !viewModel.saving,
                    modifier = Modifier.weight(1f)
                ) { Text("Сохранить") }
            }
            TextButton(
                onClick = viewModel::onClear,
                enabled = viewModel.loaded && !viewModel.saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить настройки") }
            Text(
                "Пароль хранится зашифрованно в защищённом хранилище устройства.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
