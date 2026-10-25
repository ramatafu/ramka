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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.content.ActivityNotFoundException
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.connection.ConnectionLogo
import com.ramka.app.connection.ConnectionIndicatorViewModel
import com.ramka.app.connection.resolveConnectionState
import com.ramka.app.panic.PanicViewModel
import com.ramka.app.panic.findActivity
import com.ramka.app.panic.panicLogoTrigger
import com.ramka.app.ui.call.CallViewModel
import com.ramka.app.ui.call.activityCallViewModel
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.domain.call.CallState
import com.ramka.domain.model.Contact

private enum class NotificationPermissionUiState { NONE, DENIED, PERMANENTLY_DENIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpenChat: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
    panicViewModel: PanicViewModel = hiltViewModel(),
    connectionViewModel: ConnectionIndicatorViewModel = hiltViewModel(),
    callViewModel: CallViewModel = activityCallViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()

    // Управление контактами (долгое нажатие). Храним только id: сам контакт берётся из актуального списка,
    // а rememberSaveable переживает пересоздание активити (в т.ч. пока открыта галерея).
    var menuContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var photoTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = photoTargetId
        photoTargetId = null
        if (uri != null && target != null) viewModel.updatePhoto(target, uri)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    
    // <-- СЛУШАЕМ состояние обнаружения пира -->
    val isLanPeerDiscovered by viewModel.isLanPeerDiscovered.collectAsState()
    
    val callState by callViewModel.state.collectAsState()
    val link by connectionViewModel.link.collectAsState()
    
    // <-- ИЗМЕНЕНО: добавлен параметр turnReady -->
    val connection = resolveConnectionState(
        callConnected = callState is CallState.Connected,
        network = link.network,
        relayConnected = link.relayConnected,
        peersDiscoveredInLan = isLanPeerDiscovered,
        turnReady = link.turnReady // <-- ДОБАВЛЕНО: теперь логотип знает о статусе TURN
    )
    
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasAskedNotificationPermissionBefore &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.markNotificationPermissionAsked()
            requestNotificationPermission()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionLogo(
                            state = connection,
                            modifier = Modifier
                                .panicLogoTrigger { panicViewModel.panic(context.findActivity()) }
                                .padding(8.dp)
                                .size(24.dp)
                        )
                        Text(
                            text = "ramka",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Настройки", onClick = onOpenSettings)
                                .padding(start = 2.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact, containerColor = RamkaAccent) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить контакт")
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
                        ContactRow(
                            contact = contact,
                            onClick = { onOpenChat(contact.localId) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuContactId = contact.localId
                            }
                        )
                    }
                    // Только «Избранное» в списке — подсказка, как добавить первого собеседника.
                    if (contacts.none { !it.isSelf }) {
                        item(key = "no_contacts_hint") { NoContactsHint() }
                    }
                }
            }
        }
    }

    val menuContact = menuContactId?.let { id -> contacts.firstOrNull { it.localId == id } }
    if (menuContact != null) {
        ContactActionsSheet(
            contact = menuContact,
            onDismiss = { menuContactId = null },
            onRename = {
                menuContactId = null
                renameContactId = menuContact.localId
            },
            onPickPhoto = {
                menuContactId = null
                photoTargetId = menuContact.localId
                try {
                    photoLauncher.launch("image/*")
                } catch (e: ActivityNotFoundException) {
                    photoTargetId = null
                    viewModel.onPhotoPickerUnavailable()
                }
            },
            onDelete = {
                menuContactId = null
                deleteContactId = menuContact.localId
            }
        )
    }

    val renameContact = renameContactId?.let { id -> contacts.firstOrNull { it.localId == id } }
    if (renameContact != null) {
        RenameContactDialog(
            contact = renameContact,
            onDismiss = { renameContactId = null },
            onConfirm = { name ->
                renameContactId = null
                viewModel.updateAlias(renameContact.localId, name)
            }
        )
    }

    val deleteContact = deleteContactId?.let { id -> contacts.firstOrNull { it.localId == id } }
    if (deleteContact != null) {
        DeleteContactDialog(
            contact = deleteContact,
            onDismiss = { deleteContactId = null },
            onConfirm = {
                deleteContactId = null
                viewModel.deleteContact(deleteContact.localId)
            }
        )
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
private fun NoContactsHint() {
    Text(
        "Добавьте первый контакт: нажмите «+» и отсканируйте QR-код.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hasUnread = contact.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Действия с контактом"
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(alias = contact.displayName(), photoUri = contact.photoUri, isSelf = contact.isSelf)
        Spacer(Modifier.width(12.dp))
        Text(
            contact.displayName(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (hasUnread) RamkaAccent else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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

/** Меню действий над контактом (долгое нажатие): имя, фото, удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactActionsSheet(
    contact: Contact,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onPickPhoto: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(alias = contact.displayName(), photoUri = contact.photoUri, size = 40.dp, isSelf = contact.isSelf)
                Spacer(Modifier.width(12.dp))
                Text(
                    contact.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ContactActionRow(Icons.Filled.Edit, "Изменить имя", onRename)
            // «Избранное»: фото всегда звезда, удалить нельзя — остаётся только переименование.
            if (!contact.isSelf) {
                ContactActionRow(
                    Icons.Filled.Image,
                    if (contact.photoUri == null) "Добавить фото" else "Изменить фото",
                    onPickPhoto
                )
                ContactActionRow(Icons.Filled.Delete, "Удалить контакт", onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun ContactActionRow(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

@Composable
private fun RenameContactDialog(contact: Contact, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable(contact.localId) { mutableStateOf(contact.alias) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить имя") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ContactsViewModel.MAX_ALIAS_LENGTH) },
                singleLine = true,
                label = { Text("Имя контакта") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.trim().isNotEmpty()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun DeleteContactDialog(contact: Contact, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить контакт") },
        text = { Text("Удалить контакт ${contact.displayName()}? Все сообщения будут удалены.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
