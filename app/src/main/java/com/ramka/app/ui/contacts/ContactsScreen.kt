package com.ramka.app.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.domain.model.Contact

/**
 * Экран «Список контактов» — см. PROJECT_SPEC.md, п. 8.2. Без превью сообщений.
 *
 * ПРИМЕЧАНИЕ ПО ИКОНКАМ: для «добавить по QR» и «обновить очередь» использованы
 * текстовые глифы, а не Icons.Filled.QrCodeScanner/Sync — этих иконок нет в
 * material-icons-core (только в material-icons-extended, которую по вашему
 * указанию не добавляем без отдельного одобрения — см. §4.3). Icons.Filled.Search
 * входит в core и используется как обычно.
 *
 * ЭТАП B.7/B.8, ревью п.5: здесь же — runtime-запрос POST_NOTIFICATIONS (API 33+),
 * тем же паттерном, что CAMERA в QrScreen.kt (объяснение -> запрос; отказ ->
 * «Повторить»; постоянный отказ -> «Открыть настройки»). Запрашивается
 * автоматически один раз при первом показе этого экрана (см.
 * ContactsViewModel.hasAskedNotificationPermissionBefore) — это и есть "первое
 * использование" приложения, отдельного специфического действия-триггера для
 * уведомлений, в отличие от сканирования QR, здесь нет.
 */
private enum class NotificationPermissionUiState { NONE, DENIED, PERMANENTLY_DENIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpenChat: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val context = LocalContext.current
    var notificationPermissionState by remember { mutableStateOf(NotificationPermissionUiState.NONE) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationPermissionState = NotificationPermissionUiState.NONE
        } else {
            val activity = context as? Activity
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ?: true
            notificationPermissionState = if (canAskAgain) {
                NotificationPermissionUiState.DENIED
            } else {
                NotificationPermissionUiState.PERMANENTLY_DENIED
            }
        }
    }

    fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(Unit) {
        // Только API 33+ — раньше POST_NOTIFICATIONS не существует как runtime-разрешение,
        // уведомления и так показываются без него. Автозапрос — не более одного раза.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasAskedNotificationPermissionBefore &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.markNotificationPermissionAsked()
            requestNotificationPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ramka") },
                actions = {
                    IconButton(onClick = { /* локальный поиск по псевдонимам — этап 1.x */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск")
                    }
                    // Раньше здесь была вторая кнопка «⌗», дублирующая FAB (тот же
                    // onAddContact) — убрана по указанию директивы v2, этап A.4:
                    // единственный вход в QR-экран — FAB ниже.
                    IconButton(onClick = { viewModel.refreshOutbox() }) {
                        Text("↻", style = MaterialTheme.typography.titleLarge)
                    }
                    // Вход в настройки (ЭТАП B.8): до этого onOpenSettings нигде не вызывался.
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact, containerColor = RamkaAccent) {
                Text("＋", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (notificationPermissionState) {
                NotificationPermissionUiState.DENIED -> NotificationPermissionBanner(
                    message = "Без разрешения на уведомления вы не узнаете о новых сообщениях, пока не откроете приложение.",
                    actionLabel = "Повторить",
                    onAction = { requestNotificationPermission() }
                )
                NotificationPermissionUiState.PERMANENTLY_DENIED -> NotificationPermissionBanner(
                    message = "Уведомления отключены в настройках. Включите их вручную, чтобы не пропускать сообщения.",
                    actionLabel = "Открыть настройки",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                )
                NotificationPermissionUiState.NONE -> Unit
            }

            if (contacts.isEmpty()) {
                EmptyContactsState(Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(contacts, key = { it.localId }) { contact ->
                        ContactRow(contact, onClick = { onOpenChat(contact.localId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(message: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun EmptyContactsState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Пока нет контактов.\nДобавьте первый через QR-код.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

/**
 * Контакты с unreadCount > 0 — жирным акцентным цветом, с числовым badge справа.
 * Порядок (непрочитанные вверху, дальше по алфавиту) задан на уровне SQL —
 * см. ContactDao.observeAll() — здесь только отображение, не сортировка.
 */
@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    val hasUnread = contact.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(alias = contact.alias)
        Spacer(Modifier.width(12.dp))
        Text(
            contact.alias,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (hasUnread) RamkaAccent else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Намеренно нет превью последнего сообщения — запрет п. 8.1. Badge — только число.
        if (hasUnread) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(count = contact.unreadCount)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(RamkaAccent, CircleShape)
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ContactAvatar(alias: String) {
    val initial = alias.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colorForAlias(alias)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = androidx.compose.ui.graphics.Color.White)
    }
}

/** Стабильный псевдослучайный цвет аватарки на основе псевдонима (локально, без сервера). */
private fun colorForAlias(alias: String): androidx.compose.ui.graphics.Color {
    val hue = (alias.hashCode().and(0xFFFFFF) % 360).toFloat()
    return androidx.compose.ui.graphics.Color.hsv(hue, 0.45f, 0.55f)
}
