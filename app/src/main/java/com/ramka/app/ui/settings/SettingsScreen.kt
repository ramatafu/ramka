package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.background.OemSettingsLauncher
import com.ramka.app.relay.RelayCheckUi
import com.ramka.domain.relay.RelayAddressError
import com.ramka.domain.relay.RelayConfigIssue
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayPinError

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
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
            Spacer(Modifier.height(24.dp))
            RelaySection(viewModel)
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

/**
 * Домашний relay (этап 3): адрес `host:port` (IP или домен), токен доступа и пин сертификата.
 * Relay общий для всех контактов и в QR-приглашение не попадает: оба собеседника вводят один и тот же.
 */
@Composable
private fun RelaySection(viewModel: SettingsViewModel) {
    val form by viewModel.relayForm.collectAsState()
    val lastFailure by viewModel.relayLastFailure.collectAsState()
    val errors = form.errors

    Column(Modifier.fillMaxWidth()) {
        SettingSwitchRow(
            title = "Домашний relay",
            description = "Доставка сообщений через ваш сервер ramka-relay, когда собеседник вне вашей " +
                "сети. Сервер видит только зашифрованные байты и не может их прочитать. Приём работает, " +
                "пока приложение запущено; чтобы получать сообщения в фоне, включите «Постоянный сервис».",
            checked = form.enabled,
            enabled = true,
            onCheckedChange = viewModel::onRelayEnabledChanged
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = form.address,
            onValueChange = viewModel::onRelayAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адрес relay (host:port)") },
            placeholder = { Text("relay.example.com:48766") },
            singleLine = true,
            enabled = form.enabled,
            isError = errors?.address != null,
            supportingText = { errors?.address?.let { Text(addressErrorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = form.token,
            onValueChange = viewModel::onRelayTokenChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Токен доступа") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = form.enabled,
            isError = errors?.token != null,
            supportingText = { errors?.token?.let { Text(tokenErrorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = form.pin,
            onValueChange = viewModel::onRelayPinChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Пин сертификата (SHA-256)") },
            supportingText = {
                Text(
                    errors?.pin?.let { pinErrorText(it) }
                        ?: "64 hex-символа из журнала сервера. Для домена с сертификатом Let's Encrypt можно оставить пустым."
                )
            },
            enabled = form.enabled,
            isError = errors?.pin != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::onRelaySave, enabled = form.enabled && form.dirty) { Text("Сохранить") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = viewModel::onRelayCheck,
                enabled = form.enabled && form.check != RelayCheckUi.Checking
            ) { Text("Проверить подключение") }
        }
        Spacer(Modifier.height(8.dp))
        when {
            // Ползунок выключен: поля и кнопки неактивны, подключений нет, старый результат не показываем.
            !form.enabled -> Text(
                "Релей отключён",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            form.check != RelayCheckUi.Idle -> RelayCheckStatus(form.check)
            form.active -> lastFailure?.let {
                Text(
                    "Последняя ошибка: ${failureText(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Text(
                "Укажите адрес и токен и нажмите «Сохранить»: подключение начнётся после этого.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelayCheckStatus(check: RelayCheckUi) {
    when (check) {
        RelayCheckUi.Idle -> Unit
        RelayCheckUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Проверяем подключение…", style = MaterialTheme.typography.bodyMedium)
        }
        RelayCheckUi.Ok -> Text(
            "Подключение работает: сертификат, токен и протокол в порядке.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        is RelayCheckUi.Failed -> Text(
            failureText(check.failure),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun addressErrorText(error: RelayAddressError): String = when (error) {
    RelayAddressError.EMPTY -> "Введите адрес relay"
    RelayAddressError.FORBIDDEN_CHARACTERS -> "Только host или host:port, без https://, пути и пробелов"
    RelayAddressError.BAD_HOST -> "Некорректное имя хоста или IP-адрес"
    RelayAddressError.BAD_PORT -> "Порт должен быть числом от 1 до 65535"
    RelayAddressError.IPV6_NEEDS_BRACKETS -> "IPv6-адрес указывается в квадратных скобках: [2001:db8::1]:48766"
}

private fun tokenErrorText(issue: RelayConfigIssue): String = when (issue) {
    RelayConfigIssue.NO_TOKEN -> "Введите токен доступа"
    RelayConfigIssue.BAD_TOKEN_LENGTH -> "Токен должен быть длиной от 16 до 64 байт"
    RelayConfigIssue.NO_ADDRESS, RelayConfigIssue.PIN_REQUIRED_FOR_IP -> "Некорректный токен"
}

private fun pinErrorText(error: RelayPinError): String = when (error) {
    RelayPinError.BAD_FORMAT -> "Пин — ровно 64 hex-символа (допускаются пробелы и двоеточия)"
    RelayPinError.REQUIRED_FOR_IP -> "Для IP-адреса пин обязателен: сертификат проверить иначе нечем"
}

private fun failureText(failure: RelayFailure): String = when (failure) {
    RelayFailure.UNREACHABLE -> "relay недоступен: проверьте адрес, порт, проброс порта и интернет"
    RelayFailure.TLS -> "ошибка TLS: пин сертификата не совпал или сертификат недействителен"
    RelayFailure.TLS13_UNSUPPORTED -> "это устройство не поддерживает TLS 1.3 (нужен Android 10 или новее)"
    RelayFailure.AUTH -> "relay не принял токен доступа"
    RelayFailure.PROTOCOL -> "неожиданный ответ relay: проверьте версии приложения и сервера"
    RelayFailure.TARGET_OFFLINE -> "собеседник сейчас не подключён к relay"
    RelayFailure.TIMEOUT -> "relay не ответил вовремя"
    RelayFailure.BUSY -> "relay временно ограничил доступ (слишком много запросов или неверных токенов)"
    RelayFailure.REPLACED -> "это устройство вытеснено другим устройством с теми же ключами"
    RelayFailure.INTERNAL -> "внутренняя ошибка relay"
}
