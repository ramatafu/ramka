package com.ramka.app.ui.relay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.relay.RelayCheckUi
import com.ramka.app.ui.settings.addressErrorText
import com.ramka.app.ui.settings.failureText
import com.ramka.app.ui.settings.labelErrorText
import com.ramka.app.ui.settings.pinErrorText
import com.ramka.app.ui.settings.tokenErrorText
import com.ramka.domain.relay.RelayPool

/** Редактор одного релея: метка, адрес, токен, пин, сохранение, проверка подключения, удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayEditScreen(
    onBack: () -> Unit,
    viewModel: RelayEditViewModel = hiltViewModel()
) {
    val form by viewModel.state.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    // Ошибки показываем после первого изменения, чтобы пустая новая форма не была красной.
    val errors = if (form.dirty) form.errors else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Релей" else "Новый релей") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = form.label,
                onValueChange = viewModel::onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Метка (необязательно)") },
                singleLine = true,
                isError = errors?.label != null,
                supportingText = { errors?.label?.let { Text(labelErrorText(it)) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.address,
                onValueChange = viewModel::onAddressChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес relay (host:port)") },
                placeholder = { Text("relay.example.com:48766") },
                singleLine = true,
                isError = errors?.address != null,
                supportingText = { errors?.address?.let { Text(addressErrorText(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.token,
                onValueChange = viewModel::onTokenChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Токен доступа") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = errors?.token != null,
                supportingText = { errors?.token?.let { Text(tokenErrorText(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.pin,
                onValueChange = viewModel::onPinChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пин сертификата (SHA-256)") },
                supportingText = {
                    Text(
                        errors?.pin?.let { pinErrorText(it) }
                            ?: "64 hex-символа из журнала сервера. Для домена с сертификатом Let's Encrypt можно оставить пустым."
                    )
                },
                isError = errors?.pin != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (viewModel.save()) onBack() },
                    enabled = form.canSave
                ) { Text("Сохранить") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = viewModel::check,
                    enabled = form.canCheck && form.check != RelayCheckUi.Checking
                ) { Text("Проверить подключение") }
            }
            saveError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    saveErrorText(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            RelayCheckStatus(form.check)
            if (viewModel.isEditing) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить релей?") },
            text = { Text("Запись будет удалена из списка вместе с токеном.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (viewModel.delete()) onBack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
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

private fun saveErrorText(error: RelaySaveError): String = when (error) {
    RelaySaveError.DUPLICATE_ADDRESS -> "Релей с таким адресом уже есть в списке"
    RelaySaveError.LIMIT_REACHED -> "В списке не больше ${RelayPool.MAX_ENTRIES} релеёв"
}
