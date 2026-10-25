package com.ramka.app.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextOverflow
import com.ramka.app.ui.contacts.ContactAvatar
import com.ramka.app.ui.contacts.displayName
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.app.ui.theme.RamkaBubbleOutgoing
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.MessageStatus
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// 1. Класс запроса на открытие файла
data class OpenFileRequest(val uri: Uri, val mimeType: String?, val fileName: String?)

private const val OPEN_TAG = "ChatOpenFile"

/** «Внизу» для умной автопрокрутки: первый элемент виден и сдвинут не более чем на столько dp. */
private const val NEAR_BOTTOM_DP = 100

// 2. Умное определение MIME-типа
private fun resolveMimeType(context: Context, req: OpenFileRequest): String {
    req.mimeType?.lowercase()?.trim()
        ?.takeIf { it.isNotEmpty() && it != "*/*" && it != "application/octet-stream" }
        ?.let { return it }
    
    val ext = (req.fileName ?: req.uri.lastPathSegment).orEmpty()
        .substringAfterLast('.', "").lowercase()
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    
    context.contentResolver.getType(req.uri)
        ?.takeIf { it != "application/octet-stream" }
        ?.let { return it }
    
    return "application/octet-stream"
}

// 3. Функция открытия с поддержкой FileProvider и явной выдачей прав
private fun openFileExternally(context: Context, req: OpenFileRequest): String? {
    val mime = resolveMimeType(context, req)
    
    // Конвертация file:// в content:// через FileProvider
    val safeUri = if (req.uri.scheme == "file") {
        val file = File(req.uri.path ?: return "Неверный путь к файлу")
        if (!file.exists() || !file.isFile) {
            return "Файл не найден на устройстве"
        }
        try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: IllegalArgumentException) {
            Log.e(OPEN_TAG, "FileProvider не настроен для этого пути: ${req.uri}", e)
            return "Ошибка конфигурации FileProvider"
        }
    } else {
        req.uri // Уже content:// или другой поддерживаемый тип
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(safeUri, mime)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        clipData = ClipData.newRawUri("", safeUri)
    }

    // Явная выдача прав всем целевым приложениям
    val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    for (resolveInfo in resInfoList) {
        val packageName = resolveInfo.activityInfo.packageName
        context.grantUriPermission(packageName, safeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val size = runCatching {
        context.contentResolver.openAssetFileDescriptor(safeUri, "r")?.use { it.length }
    }.getOrNull()
    
    Log.d(OPEN_TAG, "uri=$safeUri mime=$mime size=$size target_apps_count=${resInfoList.size}")

    return try {
        context.startActivity(intent)
        Log.d(OPEN_TAG, "startActivity вызван успешно")
        null
    } catch (e: ActivityNotFoundException) {
        Log.e(OPEN_TAG, "Нет приложения для $mime", e)
        "Нет приложения для открытия файлов типа $mime"
    } catch (e: SecurityException) {
        Log.e(OPEN_TAG, "SecurityException при открытии $safeUri", e)
        "Нет доступа к файлу: ${e.message}"
    } catch (e: Exception) {
        Log.e(OPEN_TAG, "Не удалось открыть $safeUri", e)
        "Не удалось открыть файл: ${e.message}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    onBack: () -> Unit,
    onCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val contact by viewModel.contact.collectAsState()
    // Определяем по id, а не по загруженному контакту: иначе кнопки звонков мигали бы, пока контакт читается из БД.
    val isSelfChat = contactId == ContactConstants.SELF_CONTACT_ID
    val messages by viewModel.messages.collectAsState()
    val notifiedFiles by viewModel.notifiedFiles.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    // Умная автопрокрутка. reverseLayout = true: индекс 0 — самое новое сообщение, оно же «низ» чата.
    val listState = rememberLazyListState()
    val nearBottomPx = with(LocalDensity.current) { NEAR_BOTTOM_DP.dp.roundToPx() }
    var stickToBottom by remember { mutableStateOf(true) }

    // «Пользователь внизу» обновляем только по реальному скроллу. Эмиссии, в которых изменилось число
    // элементов, пропускаем: при вставке нового сообщения список сам сдвигает firstVisibleItemIndex на 1
    // (сохраняя позицию по ключу), и без этого пропуска «внизу» ложно превращалось бы в «выше».
    LaunchedEffect(listState, nearBottomPx) {
        var previousCount = -1
        snapshotFlow {
            Triple(
                listState.layoutInfo.totalItemsCount,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (count, index, offset) ->
            if (count == previousCount) stickToBottom = index == 0 && offset < nearBottomPx
            previousCount = count
        }
    }

    val newest = messages.lastOrNull()
    LaunchedEffect(newest?.localId) {
        // Открытие чата / новое сообщение: вниз, если пользователь внизу. Своё отправленное сообщение
        // показываем всегда — иначе отправитель, читавший историю, не увидит собственного сообщения.
        if (newest != null && (stickToBottom || newest.direction == MessageDirection.OUTGOING)) {
            listState.scrollToItem(0)
        }
    }

    // Контекстное меню сообщения (долгое нажатие) и диалоги второго уровня.
    var menuState by remember { mutableStateOf<MessageContextMenuState?>(null) }
    var deleteTarget by remember { mutableStateOf<Message?>(null) }
    var forwardTarget by remember { mutableStateOf<Message?>(null) }
    var infoTarget by remember { mutableStateOf<MessageInfo?>(null) }
    val forwardTargets by viewModel.forwardTargets.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Сообщение исчезло (например, собеседник удалил его у всех), пока меню открыто, — закрываем меню.
    LaunchedEffect(messages) {
        menuState?.let { s ->
            if (messages.none { it.localId == s.message.localId }) menuState = null
        }
    }

    val showMessage: (String) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { text -> showMessage(text) }
    }

    // LaunchedEffect, который обрабатывает запрос на открытие
    LaunchedEffect(viewModel) {
        viewModel.openFileRequest.collect { req ->
            Log.d(OPEN_TAG, "Получен запрос на открытие: ${req.uri}")
            openFileExternally(context, req)?.let { showMessage(it) }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onChatShown()
                Lifecycle.Event.ON_STOP -> viewModel.onChatHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onChatHidden()
            viewModel.clearDecryptedFileCache()
        }
    }

    Scaffold(
        // Вертикальные отступы (статус-бар, навигация, клавиатура) раскладываем сами: верх — TopAppBar,
        // низ — Column ниже (imePadding один раз). По горизонтали (вырезы, ландшафт) Scaffold оставляем.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        contact?.let {
                            ContactAvatar(alias = it.displayName(), photoUri = it.photoUri, size = 36.dp, isSelf = it.isSelf)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            contact?.displayName() ?: "…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Иконки звонков в стиле остальных значков экрана (стрелка «Назад», «Отправить»):
                    // однотонные Material, цвет акцента Ramka; без контакта — приглушены.
                    // В «Избранном» звонков нет: звонить самому себе бессмысленно.
                    if (!isSelfChat) {
                        val callTint = RamkaAccent.copy(alpha = if (contact != null) 1f else 0.38f)
                        IconButton(onClick = onVideoCall, enabled = contact != null) {
                            Icon(Icons.Filled.Videocam, contentDescription = "Видеозвонок", tint = callTint)
                        }
                        IconButton(onClick = onCall, enabled = contact != null) {
                            Icon(Icons.Filled.Call, contentDescription = "Аудиозвонок", tint = callTint)
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Без bottomBar: поле ввода — обычный нижний элемент Column. imePadding() здесь единственный:
        // Column заканчивается ровно над клавиатурой, список (weight) занимает всё место между
        // TopAppBar и полем ввода, а поле прижато к клавиатуре без зазора.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                reverseLayout = true
            ) {
                items(messages.asReversed(), key = { it.localId }) { message ->
                    MessageBubble(
                        message = message,
                        onRetryFile = viewModel::retryFile,
                        offlineSignalSent = message.localId in notifiedFiles,
                        onOpenFile = { body ->
                            body.uri?.let { uri ->
                                viewModel.onFileClicked(uri, body.fileName, body.mimeType)
                            }
                        },
                        menuState = menuState?.takeIf { it.message.localId == message.localId },
                        onLongClick = { m, position ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState = MessageContextMenuState(m, position)
                        },
                        onDismissMenu = { menuState = null },
                        onMenuAction = { action ->
                            val target = message
                            menuState = null
                            when (action) {
                                MessageAction.Copy -> viewModel.copyMessage(target)
                                MessageAction.Forward -> forwardTarget = target
                                MessageAction.Delete -> deleteTarget = target
                                MessageAction.Info -> infoTarget = viewModel.getMessageInfo(target)
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // reverseLayout: последний в коде = самый верхний на экране, поэтому подсказка о шифровании
                // идёт ПОСЛЕ сообщений. Это один item без записи в БД: индекс 0 по-прежнему самое новое
                // сообщение, автопрокрутка не затронута, а в пустом чате подсказка всё равно видна.
                item(key = ENCRYPTION_BANNER_KEY, contentType = "encryption_banner") {
                    EncryptionInfoBanner()
                }
            }
            // Snackbar в потоке Column: показывается над полем ввода и не перекрывается клавиатурой.
            SnackbarHost(snackbarHostState)
            MessageInputBar(
                text = viewModel.draftText,
                onTextChange = viewModel::onDraftChanged,
                onSend = viewModel::sendDraft,
                onAttach = { filePickerLauncher.launch("*/*") }
            )
        }
    }

    deleteTarget?.let { target ->
        DeleteMessageDialog(
            message = target,
            onDeleteForMe = {
                deleteTarget = null
                viewModel.deleteMessageLocally(target.localId)
            },
            onDeleteForEveryone = {
                deleteTarget = null
                viewModel.deleteMessageForEveryone(target.localId)
            },
            onDismiss = { deleteTarget = null }
        )
    }
    forwardTarget?.let { target ->
        ForwardDialog(
            contacts = forwardTargets,
            onSelect = { contact ->
                forwardTarget = null
                viewModel.forwardMessage(target, contact.localId)
            },
            onDismiss = { forwardTarget = null }
        )
    }
    infoTarget?.let { info ->
        MessageInfoDialog(info = info, onDismiss = { infoTarget = null })
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onRetryFile: (String) -> Unit,
    offlineSignalSent: Boolean,
    onOpenFile: (MessageBody.File) -> Unit,
    menuState: MessageContextMenuState?,
    onLongClick: (Message, Offset) -> Unit,
    onDismissMenu: () -> Unit,
    onMenuAction: (MessageAction) -> Unit
) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val bubbleColor = if (isOutgoing) RamkaBubbleOutgoing else RamkaAccent.copy(alpha = 0.18f)
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        // Внешний Box — якорь для меню: координаты нажатия считаются от пузыря, а Popup ставится от него же.
        Box(modifier = Modifier.widthIn(max = 280.dp)) {
            Column(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(14.dp))
                    .messageLongPress { offset -> onLongClick(message, offset) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (val body = message.body) {
                    is MessageBody.Text -> Text(
                        text = body.text,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    is MessageBody.File -> FileBubble(
                        body = body,
                        isOutgoing = isOutgoing,
                        status = message.status,
                        onRetry = { onRetryFile(message.localId) },
                        onOpen = { onOpenFile(body) },
                        signalSent = offlineSignalSent
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatLocalTime(message.localCreatedAtEpochMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    // Заметки в «Избранном» никуда не доставляются — галочки статуса там не нужны.
                    if (isOutgoing && message.contactId != ContactConstants.SELF_CONTACT_ID) {
                        Spacer(Modifier.width(4.dp))
                        StatusTicks(message.status)
                    }
                }
            }
            if (menuState != null) {
                MessageContextMenu(
                    state = menuState,
                    onDismiss = onDismissMenu,
                    onAction = onMenuAction
                )
            }
        }
    }
}

@Composable
private fun StatusTicks(status: MessageStatus) {
    val (icon, color) = when (status) {
        MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.FAILED ->
            Icons.Filled.Schedule to MaterialTheme.colorScheme.secondary
        MessageStatus.DELIVERED -> Icons.Filled.Done to MaterialTheme.colorScheme.secondary
        MessageStatus.READ -> Icons.Filled.DoneAll to RamkaAccent
    }
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit
) {
    // imePadding здесь НЕ нужен (он на родительском Column). navigationBarsPadding стоит внутри Surface:
    // фон поля ввода доходит до края экрана под панелью навигации; при открытой клавиатуре этот
    // отступ автоматически становится 0, потому что Column уже «съел» нижний inset.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить файл", tint = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4
            )
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = RamkaAccent)
            }
        }
    }
}

private fun formatLocalTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))