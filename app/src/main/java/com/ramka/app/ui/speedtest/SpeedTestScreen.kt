package com.ramka.app.ui.speedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramka.app.speedtest.SpeedQuality
import com.ramka.app.speedtest.SpeedTestAnalysis
import com.ramka.app.speedtest.SpeedTestOutcome
import com.ramka.app.speedtest.SpeedTestSkip
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val GoodColor = Color(0xFF2E7D32)
private val FairColor = Color(0xFFF9A825)
private val PoorColor = Color(0xFFC62828)

private fun SpeedQuality.color(): Color = when (this) {
    SpeedQuality.GOOD -> GoodColor
    SpeedQuality.FAIR -> FairColor
    SpeedQuality.POOR -> PoorColor
}

/** Проверка скорости: поле URL (необязательно), кнопка «Начать тест», прогресс, результаты и рекомендации. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    viewModel: SpeedTestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проверка скорости") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.testUrl,
                onValueChange = viewModel::onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isTesting && state.urlLoaded,
                label = { Text("URL для теста скорости") },
                placeholder = { Text("https://example.com/test-10mb.bin") },
                singleLine = true,
                isError = state.urlError != null,
                supportingText = {
                    Text(
                        state.urlError
                            ?: "Необязательно. Без адреса измеряются только ping и jitter до relay. " +
                            "Для скорости нужен файл около 10 МБ на вашем сервере."
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Spacer(Modifier.height(24.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isTesting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stageText(state.stage), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Button(
                        onClick = { viewModel.runTest() },
                        enabled = state.urlError == null && state.urlLoaded,
                        modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                    ) {
                        Text("Начать тест", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            state.result?.let { outcome ->
                Spacer(Modifier.height(24.dp))
                ResultSection(outcome, onForceRerun = { viewModel.runTest(force = true) }, enabled = !state.isTesting)
            }
        }
    }
}

@Composable
private fun ResultSection(outcome: SpeedTestOutcome, onForceRerun: () -> Unit, enabled: Boolean) {
    val test = outcome.test
    val skipped = outcome.skipped
    val urlUsed = SpeedTestSkip.NO_URL !in skipped

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            title = "Ping",
            value = test.pingMs?.let { "$it мс" },
            quality = test.pingMs?.let { SpeedTestAnalysis.pingQuality(it) },
            caption = if (test.pingMs != null) {
                "Время полного подключения к relay (TCP + TLS + проверка токена), поэтому оно больше обычного ping."
            } else {
                missingCaption(skipped, SpeedTestSkip.NO_RELAY, SpeedTestSkip.RELAY_FAILED)
            }
        )
        MetricCard(
            title = "Jitter",
            value = test.jitterMs?.let { "$it мс" },
            quality = test.jitterMs?.let { SpeedTestAnalysis.jitterQuality(it) },
            caption = if (test.jitterMs != null) null else missingCaption(skipped, SpeedTestSkip.NO_RELAY, SpeedTestSkip.RELAY_FAILED)
        )
        if (urlUsed) {
            MetricCard(
                title = "Download",
                value = test.downloadSpeedMbps?.let { formatMbps(it) },
                quality = test.downloadSpeedMbps?.let { SpeedTestAnalysis.speedQuality(it) },
                caption = if (test.downloadSpeedMbps != null) null else missingCaption(skipped, SpeedTestSkip.DOWNLOAD_FAILED)
            )
            if (test.downloadSpeedMbps != null) {
                MetricCard(
                    title = "Upload",
                    value = test.uploadSpeedMbps?.let { formatMbps(it) },
                    quality = test.uploadSpeedMbps?.let { SpeedTestAnalysis.speedQuality(it) },
                    caption = if (test.uploadSpeedMbps != null) null else missingCaption(skipped, SpeedTestSkip.UPLOAD_UNSUPPORTED)
                )
            }
        }

        val recommendations = SpeedTestAnalysis.recommendations(test)
        if (recommendations.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Рекомендации", style = MaterialTheme.typography.titleSmall)
                    recommendations.forEach { Text(recommendationText(it), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(test.timestamp))
        if (outcome.fromCache) {
            Text(
                "Показан результат теста от $time: новый тест выполняется не чаще раза в час.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            TextButton(onClick = onForceRerun, enabled = enabled) { Text("Всё равно повторить") }
        } else {
            Text(
                "Тест от $time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun missingCaption(skipped: Set<SpeedTestSkip>, vararg reasons: SpeedTestSkip): String? =
    reasons.firstOrNull { it in skipped }?.let { skipCaption(it) }

private fun formatMbps(value: Double): String = String.format(Locale.getDefault(), "%.1f Мбит/с", value)

@Composable
private fun MetricCard(title: String, value: String?, quality: SpeedQuality?, caption: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    value ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = quality?.color() ?: MaterialTheme.colorScheme.onSurface
                )
                if (quality != null) {
                    Spacer(Modifier.width(8.dp))
                    // Слово дублирует цвет, чтобы оценка читалась и без различения цветов.
                    Text(qualityText(quality), style = MaterialTheme.typography.bodyMedium, color = quality.color())
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
