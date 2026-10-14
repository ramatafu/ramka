package com.ramka.app.ui.turn

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Добавление и редактирование TURN-сервера: адрес, логин, пароль (с кнопкой «Показать»), «Сохранить» / «Отмена». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnServerEditScreen(
    onBack: () -> Unit,
    viewModel: TurnServerEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    val fieldsEnabled = !state.isLoading && !state.saving

    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "TURN-сервер" else "Новый TURN-сервер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = fieldsEnabled,
                label = { Text("Адрес сервера") },
                placeholder = { Text("turn:203.0.113.5:3478") },
                singleLine = true,
                isError = state.urlError != null,
                supportingText = { state.urlError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = fieldsEnabled,
                label = { Text("Логин") },
                singleLine = true,
                isError = state.usernameError != null,
                supportingText = { state.usernameError?.let { Text(it) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = fieldsEnabled,
                label = { Text(if (state.isEditing) "Пароль (пусто — не менять)" else "Пароль") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Скрыть" else "Показать")
                    }
                },
                isError = state.passwordError != null,
                supportingText = { state.passwordError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Отмена") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = viewModel::save, enabled = fieldsEnabled, modifier = Modifier.weight(1f)) {
                    Text("Сохранить")
                }
            }
        }
    }
}
