package com.ramka.app.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private const val GITHUB_TEXT = "github.com/ramatafu/ramka"
private const val GITHUB_URL = "https://github.com/ramatafu/ramka"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { readVersionName(context) }
    val uriHandler = LocalUriHandler.current

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
                color = MaterialTheme.colorScheme.onSurface
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
                    // Нет браузера на устройстве — не падаем, просто ничего не делаем.
                    runCatching { uriHandler.openUri(GITHUB_URL) }
                })
                AboutLine("Лицензия: GNU GPLv3")
            }

            Spacer(Modifier.height(48.dp))
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
        // Содержимое выровнено по тексту заголовка (20.dp иконка + 12.dp отступ).
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

/** Ссылка цвета primary; подчёркивается, пока палец на ней. */
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

/**
 * Версия берётся из PackageManager, а не из BuildConfig: в app/build.gradle.kts не включён
 * buildFeatures.buildConfig, и ради одной строки менять сборку не нужно. Значение то же —
 * RAMKA_VERSION_NAME из gradle.properties.
 */
@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: PackageManager.NameNotFoundException) {
        "—"
    }
