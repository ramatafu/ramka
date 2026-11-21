package com.ramka.app.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Экран «Yggdrasil»: два независимых переключателя и карточка Yggmail.
 * Yggmail это независимый резервный канал (обычные SMTP/IMAP, как в Delta Chat): его переключатель виден всегда,
 * даже когда Yggdrasil выключен (см. [shouldShowYggmailToggle]). Поля Yggmail раскрываются в карточке с анимацией.
 * Логика сохранения в [SettingsViewModel] не менялась.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YggdrasilSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yggdrasil") },
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
            SettingsCard {
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
            }
            if (shouldShowYggmailToggle(viewModel.yggdrasil, viewModel.yggmailEnabled)) {
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    SettingSwitchRow(
                        title = "Использовать Yggmail",
                        description = "Резерв: сообщения уходят письмом через ваш Yggmail-сервер, когда прямые каналы и relay недоступны. Необязательно.",
                        checked = viewModel.yggmailEnabled,
                        enabled = true,
                        onCheckedChange = viewModel::onYggmailEnabledChanged
                    )
                }
            }

            // Карточка раскрывается и сворачивается плавно; отступ сверху внутри условия, чтобы он тоже исчезал.
            Column(Modifier.animateContentSize()) {
                if (viewModel.yggmailEnabled) {
                    Spacer(Modifier.height(12.dp))
                    YggmailCard(viewModel)
                }
            }
        }
    }

    // Разовая подсказка про фоновую работу создаётся вместе с ViewModel: показываем её и здесь, чтобы не потерять.
    BackgroundWorkHintDialog(viewModel)
}

/**
 * Необязательный канал Yggmail: письма через ваш Yggmail-сервер как последний резерв, когда прямых каналов и relay нет.
 */
@Composable
private fun YggmailCard(viewModel: SettingsViewModel) {
    val form = viewModel.yggmailForm
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
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
    }
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
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                number -> KeyboardType.Number
                password -> KeyboardType.Password
                else -> KeyboardType.Uri
            }
        )
    )
}
