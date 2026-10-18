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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.app.ui.theme.RamkaBubbleOutgoing
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// 1. Класс запроса на открытие файла
data class OpenFileRequest(val uri: Uri, val mimeType: String?, val fileName: String?)

private const val OPEN_TAG = "ChatOpenFile"

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
    val messages by viewModel.messages.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFilePicked(uri)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(contact?.alias ?: "…") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onVideoCall, enabled = contact != null) {
                        Text("📹", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onCall, enabled = contact != null) {
                        Text("📞", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                text = viewModel.draftText,
                onTextChange = viewModel::onDraftChanged,
                onSend = viewModel::sendDraft,
                onAttach = { filePickerLauncher.launch("*/*") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messages.asReversed(), key = { it.localId }) { message ->
                MessageBubble(
                    message = message,
                    onRetryFile = viewModel::retryFile,
                    onOpenFile = { body -> 
                        body.uri?.let { uri ->
                            viewModel.onFileClicked(uri, body.fileName, body.mimeType)
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onRetryFile: (String) -> Unit,
    onOpenFile: (MessageBody.File) -> Unit
) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val bubbleColor = if (isOutgoing) RamkaBubbleOutgoing else RamkaAccent.copy(alpha = 0.18f)
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(14.dp))
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
                    onOpen = { onOpenFile(body) }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatLocalTime(message.localCreatedAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (isOutgoing) {
                    Spacer(Modifier.width(4.dp))
                    StatusTicks(message.status)
                }
            }
        }
    }
}

@Composable
private fun StatusTicks(status: MessageStatus) {
    val (text, color) = when (status) {
        MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.FAILED ->
            "🕐" to MaterialTheme.colorScheme.secondary
        MessageStatus.DELIVERED -> "✓" to MaterialTheme.colorScheme.secondary
        MessageStatus.READ -> "✓✓" to RamkaAccent
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit
) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Text("📎", style = MaterialTheme.typography.titleLarge)
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