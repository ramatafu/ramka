package com.ramka.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramka.app.background.OemSettingsLauncher

/** Карточка со скруглением 12 dp без заголовка: в неё кладётся один переключатель или блок. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Кнопка-«капсула» перехода: скруглённый прямоугольник на всю ширину, текст по центру. */
@Composable
internal fun NavCapsule(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/**
 * Подсказка «Фоновая работа» (инструкция под производителя телефона). Показывается один раз при первом создании
 * [SettingsViewModel] и по ссылке «Как разрешить фоновую работу». Подключена на каждом экране, который создаёт этот
 * ViewModel, иначе разовая подсказка могла бы «сгореть» на экране без диалога.
 */
@Composable
internal fun BackgroundWorkHintDialog(viewModel: SettingsViewModel) {
    if (!viewModel.showHint) return
    val context = LocalContext.current
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

@Composable
internal fun SettingSwitchRow(
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
 * Переключатель Yggmail виден ВСЕГДА, при любых состояниях Yggdrasil и самого Yggmail: Yggmail это независимый
 * резервный канал через обычные SMTP/IMAP (как в Delta Chat), и включить его можно в любой момент, даже когда
 * Yggdrasil выключен. Функция оставлена как единая точка этого правила (и ради теста), параметры не используются.
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldShowYggmailToggle(yggdrasilEnabled: Boolean, yggmailEnabled: Boolean): Boolean = true
