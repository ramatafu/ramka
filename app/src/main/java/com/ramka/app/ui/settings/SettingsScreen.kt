package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.background.OemSettingsLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNetworkServers: () -> Unit,
    onOpenProxySettings: () -> Unit, // <-- ДОБАВЛЕНО: навигация к экрану прокси
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
                .padding(16.dp)
        ) {
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
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Звук при входящем звонке",
                description = "Играть рингтон, пока звонит собеседник.",
                checked = viewModel.callRingtone,
                enabled = true,
                onCheckedChange = viewModel::onCallRingtoneChanged
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Вибрация при входящем звонке",
                description = "Вибрировать, пока звонит собеседник, в том числе в беззвучном режиме.",
                checked = viewModel.callVibration,
                enabled = true,
                onCheckedChange = viewModel::onCallVibrationChanged
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Использовать Yggdrasil",
                description = if (viewModel.yggdrasilInterfaceFound) {
                    "Интерфейс Yggdrasil найден. Контакты с Yggmail-адресом связываются напрямую по сети Yggdrasil, без relay."
                } else {
                    "Интерфейс Yggdrasil не найден: запустите приложение Yggdrasil на этом устройстве. Пока его нет, канал не используется."
                },
                checked = viewModel.yggdrasil,
                enabled = true,
                onCheckedChange = viewModel::onYggdrasilChanged
            )
            Spacer(Modifier.height(16.dp))
            YggmailSettingsSection(viewModel)

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onOpenNetworkServers, modifier = Modifier.fillMaxWidth()) {
                Text("Сеть и серверы")
            }
            Spacer(Modifier.height(8.dp))
            
            // <-- ДОБАВЛЕНА КНОПКА НАСТРОЕК ПРОКСИ
            OutlinedButton(onClick = onOpenProxySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Настройки прокси")
            }
            
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("О приложении")
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = viewModel::openHintManually, 
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
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
 * Необязательный канал Yggmail: письма через ваш Yggmail-сервер как последний резерв, когда прямых каналов и relay нет.
 * Пока тумблер выключен, поля скрыты, а приложение работает так, будто функции нет.
 */
@Composable
private fun YggmailSettingsSection(viewModel: SettingsViewModel) {
    SettingSwitchRow(
        title = "Использовать Yggmail",
        description = "Резерв: сообщения уходят письмом через ваш Yggmail-сервер, когда прямые каналы и relay недоступны. Необязательно.",
        checked = viewModel.yggmailEnabled,
        enabled = true,
        onCheckedChange = viewModel::onYggmailEnabledChanged
    )
    if (!viewModel.yggmailEnabled) return

    val form = viewModel.yggmailForm
    Spacer(Modifier.height(8.dp))
    if (!viewModel.yggmailSealerAvailable) {
        Text(
            "Шифрование офлайн-сообщений ещё не подключено: канал пока ничего не отправляет и не принимает.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
    }
    YggmailField("SMTP-сервер", form.smtpHost, "[200:db8::1] или имя сервера") { viewModel.onYggmailFormChanged(form.copy(smtpHost = it)) }
    YggmailField("SMTP-порт", form.smtpPort, "1025", number = true) { viewModel.onYggmailFormChanged(form.copy(smtpPort = it)) }
    YggmailField("IMAP-сервер", form.imapHost, "[200:db8::1] или имя сервера") { viewModel.onYggmailFormChanged(form.copy(imapHost = it)) }
    YggmailField("IMAP-порт", form.imapPort, "1143", number = true) { viewModel.onYggmailFormChanged(form.copy(imapPort = it)) }
    YggmailField("Логин", form.login, "адрес@yggmail") { viewModel.onYggmailFormChanged(form.copy(login = it)) }
    YggmailField(
        label = "Пароль",
        value = form.password,
        placeholder = if (viewModel.yggmailPasswordSaved) "сохранён; пусто — не менять" else "",
        password = true
    ) { viewModel.onYggmailFormChanged(form.copy(password = it)) }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::saveYggmail, enabled = !viewModel.yggmailBusy) { Text("Сохранить") }
        OutlinedButton(onClick = viewModel::testYggmail, enabled = !viewModel.yggmailBusy) { Text("Проверить") }
    }
    viewModel.yggmailStatus?.let { status ->
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
    if (viewModel.yggmailLog.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Журнал обмена с сервером (пароль скрыт; текст можно выделить и скопировать):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        SelectionContainer {
            Text(
                viewModel.yggmailLog.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Пароль хранится зашифрованным (Android Keystore). Порты 465/993 — SSL, 587/143 — STARTTLS, остальные — без TLS: " +
            "внутри сети Yggdrasil трафик шифруется самой сетью. IPv6-адреса можно вводить с квадратными скобками и без.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun YggmailField(
    label: String,
    value: String,
    placeholder: String,
    number: Boolean = false,
    password: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                number -> KeyboardType.Number
                password -> KeyboardType.Password
                else -> KeyboardType.Uri
            }
        )
    )
}