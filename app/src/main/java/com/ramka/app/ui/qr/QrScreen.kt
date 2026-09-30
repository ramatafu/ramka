package com.ramka.app.ui.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Экран «QR» — два режима, одноразовый код с верификацией после добавления (п. 8.4).
 *
 * Разрешение CAMERA запрашивается runtime, СТРОГО по нажатию вкладки «Сканировать» —
 * не при открытии экрана и не при старте приложения (см. RamkaApp.onCreate — там
 * никаких запросов разрешений нет). При отказе показывается объяснение с кнопкой
 * «Повторить»; при постоянном отказе (пользователь выбрал «не спрашивать снова» —
 * определяется по shouldShowRequestPermissionRationale() сразу после отказа) —
 * кнопка «Открыть настройки приложения».
 */
private enum class CameraPermissionUiState { NONE, DENIED, PERMANENTLY_DENIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(onDone: () -> Unit, viewModel: QrViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf(0) }
    val myInvite by viewModel.myInviteText.collectAsState()
    val pendingVerification by viewModel.pendingVerification.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current
    var cameraPermissionState by remember { mutableStateOf(CameraPermissionUiState.NONE) }

    val scanOptions = remember {
        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::onQrScanned)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionState = CameraPermissionUiState.NONE
            scanLauncher.launch(scanOptions)
        } else {
            // shouldShowRequestPermissionRationale() сразу после отказа: true — можно
            // спросить ещё раз и стоит объяснить зачем; false — система больше не
            // покажет диалог сама («не спрашивать снова» либо политика устройства).
            val activity = context as? Activity
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: true
            cameraPermissionState = if (canAskAgain) {
                CameraPermissionUiState.DENIED
            } else {
                CameraPermissionUiState.PERMANENTLY_DENIED
            }
        }
    }

    fun startScan() {
        tab = 1
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionState = CameraPermissionUiState.NONE
            scanLauncher.launch(scanOptions)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) { viewModel.generateMyInvite() }

    Scaffold(topBar = { TopAppBar(title = { Text("Добавить контакт") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Показать мой QR") })
                Tab(selected = tab == 1, onClick = { startScan() }, text = { Text("Сканировать") })
            }

            Spacer(Modifier.height(24.dp))

            when {
                tab == 0 -> {
                    myInvite?.let { QrCodeImage(it) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Покажите этот код собеседнику. Приглашение одноразовое и действует ограниченное время.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                cameraPermissionState == CameraPermissionUiState.DENIED -> {
                    CameraPermissionExplanation(
                        message = "Без доступа к камере нельзя отсканировать QR-код собеседника.",
                        actionLabel = "Повторить",
                        onAction = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
                cameraPermissionState == CameraPermissionUiState.PERMANENTLY_DENIED -> {
                    CameraPermissionExplanation(
                        message = "Доступ к камере отключён в настройках. Включите его вручную, чтобы сканировать QR-коды.",
                        actionLabel = "Открыть настройки приложения",
                        onAction = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingVerification?.let { pending ->
        KeyVerificationDialog(
            fingerprint = pending.fingerprint,
            onConfirm = { alias -> viewModel.confirmAddContact(alias); onDone() },
            onDismiss = viewModel::cancelVerification
        )
    }
}

@Composable
private fun CameraPermissionExplanation(message: String, actionLabel: String, onAction: () -> Unit) {
    Column {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun QrCodeImage(text: String) {
    val bitmap = remember(text) {
        val writer = QRCodeWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        bmp
    }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Мой QR-код")
}

/** Экран верификации ключа (п. 8.4) — отпечаток для сверки "из рук в руки" перед добавлением. */
@Composable
private fun KeyVerificationDialog(fingerprint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var alias by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Верификация ключа") },
        text = {
            Column {
                Text("Сверьте отпечаток с тем, что видит собеседник на своём устройстве:")
                Spacer(Modifier.height(8.dp))
                Text(
                    fingerprint,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF7DD3FC)
                )
                Spacer(Modifier.height(12.dp))
                Text("Если отпечатки совпадают — ключ подлинный. Задайте локальный псевдоним:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = alias, onValueChange = { alias = it }, placeholder = { Text("Псевдоним") })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(alias.text) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
