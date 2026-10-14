package com.ramka.app.ui.speedtest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.speedtest.NetworkSpeedTester
import com.ramka.app.speedtest.SpeedTestOutcome
import com.ramka.app.speedtest.SpeedTestSettings
import com.ramka.app.speedtest.SpeedTestStage
import com.ramka.app.speedtest.SpeedTestUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpeedTestState(
    val isTesting: Boolean = false,
    val stage: SpeedTestStage? = null,
    val result: SpeedTestOutcome? = null,
    val error: String? = null,
    val testUrl: String = "",
    val urlError: String? = null,
    val urlLoaded: Boolean = false
)

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val tester: NetworkSpeedTester,
    private val settings: SpeedTestSettings
) : ViewModel() {

    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = try {
                settings.urlFlow.first()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                ""
            }
            // Если пользователь уже начал вводить адрес, его ввод важнее сохранённого.
            _state.update { if (it.testUrl.isEmpty()) it.copy(testUrl = saved, urlLoaded = true) else it.copy(urlLoaded = true) }
        }
    }

    /** Пустая строка допустима (тест только Ping/Jitter). Корректный адрес сохраняется сразу. */
    fun onUrlChanged(value: String) {
        val trimmed = value.trim()
        val normalized = SpeedTestUrl.parse(value)
        val error = if (trimmed.isEmpty() || normalized != null) null else URL_ERROR
        _state.update { it.copy(testUrl = value, urlError = error) }
        if (trimmed.isEmpty() || normalized != null) {
            viewModelScope.launch {
                try {
                    settings.setUrl(normalized.orEmpty())
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "Не удалось сохранить URL теста скорости: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /** @param force true — игнорировать кэш (результат не старше часа показывается без нового теста) */
    fun runTest(force: Boolean = false) {
        val s = _state.value
        if (s.isTesting || s.urlError != null) return
        val url = SpeedTestUrl.parse(s.testUrl)
        _state.update { it.copy(isTesting = true, stage = null, error = null) }
        viewModelScope.launch {
            try {
                val outcome = tester.runSpeedTest(url, force) { stage -> _state.update { it.copy(stage = stage) } }
                _state.update {
                    if (outcome.test.hasAnyMeasurement) it.copy(result = outcome, error = null)
                    else it.copy(result = null, error = emptyResultMessage(outcome.skipped))
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Только тип исключения: адреса и токены в лог не попадают.
                Log.w(TAG, "Тест скорости не выполнен: ${e.javaClass.simpleName}")
                _state.update { it.copy(error = "Тест не удался. Попробуйте ещё раз.") }
            } finally {
                _state.update { it.copy(isTesting = false, stage = null) }
            }
        }
    }

    companion object {
        const val URL_ERROR = "Нужен адрес вида https://хост/путь (только https, без логина в адресе)"
        private const val TAG = "ramka"
    }
}
