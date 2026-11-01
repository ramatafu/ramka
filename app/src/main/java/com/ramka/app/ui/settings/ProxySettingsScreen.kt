package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    viewModel: ProxySettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val config by viewModel.proxyConfig.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки прокси") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Главный переключатель
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Использовать прокси", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { viewModel.setEnabled(it) }
                )
            }

            // Поля конфигурации (показываются только если прокси включен)
            if (config.isEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Тип прокси
                        Text("Тип прокси", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = config.type == "SOCKS5",
                                onClick = { viewModel.setType("SOCKS5") },
                                label = { Text("SOCKS5") }
                            )
                            FilterChip(
                                selected = config.type == "HTTP",
                                onClick = { viewModel.setType("HTTP") },
                                label = { Text("HTTP") }
                            )
                        }

                        // Хост
                        OutlinedTextField(
                            value = config.host,
                            onValueChange = { viewModel.setHost(it) },
                            label = { Text("Хост (IP или домен)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Порт
                        OutlinedTextField(
                            value = if (config.port == 0) "" else config.port.toString(),
                            onValueChange = { viewModel.setPort(it) },
                            label = { Text("Порт") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        // Логин (опционально)
                        OutlinedTextField(
                            value = config.username,
                            onValueChange = { viewModel.setUsername(it) },
                            label = { Text("Логин (опционально)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Пароль (опционально, скрытый по умолчанию)
                        OutlinedTextField(
                            value = config.password,
                            onValueChange = { viewModel.setPassword(it) },
                            label = { Text("Пароль (опционально)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                                    )
                                }
                            },
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}