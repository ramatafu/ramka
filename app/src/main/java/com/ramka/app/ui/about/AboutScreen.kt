package com.ramka.app.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val GITHUB_TEXT = "github.com/ramatafu/ramka"
private const val GITHUB_URL = "https://github.com/ramatafu/ramka"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { readVersionName(context) }
    val uriHandler = LocalUriHandler.current

    // Логика пасхалки: счётчик нажатий и флаг показа терминала
    var tapCount by remember { mutableStateOf(0) }
    var showTerminal by remember { mutableStateOf(false) }

    // Сбрасываем счётчик, если пользователь перестал нажимать на 1.5 секунды
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(1500)
            tapCount = 0
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("О приложении Ramka") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Версия $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Скрытый клик без визуальной ряби (indication = null)
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    tapCount++
                    if (tapCount >= 7) {
                        showTerminal = true
                        tapCount = 0
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
            AboutSection(icon = Icons.Filled.Code, title = "Разработка") {
                AboutLine("ram — Идея, архитектура, код")
                AboutLine("Claude Code — Генерация кода")
                AboutLine("Qwen — Консультации")
            }

            Spacer(Modifier.height(24.dp))
            AboutSection(icon = Icons.Filled.Check, title = "Тестирование") {
                AboutLine("valuka & zymban")
            }

            Spacer(Modifier.height(24.dp))
            AboutSection(icon = Icons.Filled.Security, title = "Безопасность") {
                AboutLine("Сквозное шифрование (Noise IK + AEAD)")
                AboutLine("Perfect Forward Secrecy (PFS)")
                AboutLine("Android Keystore")
            }

            Spacer(Modifier.height(24.dp))
            AboutSection(icon = Icons.Filled.Link, title = "Ресурсы") {
                GithubLink(onClick = {
                    runCatching { uriHandler.openUri(GITHUB_URL) }
                })
                AboutLine("Лицензия: GNU GPLv3")
            }

            Spacer(Modifier.height(120.dp))
            
            val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            Text(
                text = "Я не хочу знать, кто ты такой. Ты только что появился здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "© 2026 ramatafu",
                style = MaterialTheme.typography.bodySmall,
                color = faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // Показываем оверлей терминала, если он активирован
    if (showTerminal) {
        TerminalEasterEgg(onDismiss = { showTerminal = false })
    }
}

@Composable
private fun AboutSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier.padding(start = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AboutLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun GithubLink(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Text(
        text = GITHUB_TEXT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = if (pressed) TextDecoration.Underline else TextDecoration.None,
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    )
}

@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: PackageManager.NameNotFoundException) {
        "—"
    }

// ============================================================================
// ПАСХАЛКА: Терминал в стиле Kali Linux
// ============================================================================

private val KaliGreen = Color(0xFF00FF41)

@Composable
fun TerminalEasterEgg(onDismiss: () -> Unit) {
    var cursorVisible by remember { mutableStateOf(true) }

    // Мигающий курсор (классический терминальный ритм 500мс)
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    // Закрытие по системной кнопке "Назад"
    BackHandler(enabled = true) {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() } // Закрытие по тапу в любом месте экрана
    ) {
        Column(
            modifier = Modifier
                // ИЗМЕНЕНИЕ ЗДЕСЬ: Опустили текст вниз (top = 120.dp)
                .padding(top = 120.dp, start = 24.dp, end = 24.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "Ramka OS v1.0.0 (tty1)\nRamka: a privacy-first P2P messenger.\n\n",
                color = KaliGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "root@ramka:~# ",
                    color = KaliGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
                // Мигающий блок-курсор
                Text(
                    text = "█",
                    color = if (cursorVisible) KaliGreen else Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
            }
            Text(
                text = "[ нажми Назад , чтобы выйти ]",
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 48.dp)
            )
        }
    }
}