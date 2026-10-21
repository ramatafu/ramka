package com.ramka.app.ui.turn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.data.TurnServerEntry
import com.ramka.app.data.TurnSettingsDataStore
import com.ramka.app.data.TurnUrl
import com.ramka.app.turn.TurnServerChecker
import com.ramka.app.turn.TurnServerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Список TURN-серверов: показ, включение/выключение, порядок, удаление, статус доступности. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TurnServerListViewModel @Inject constructor(
    private val store: TurnSettingsDataStore,
    private val checker: TurnServerChecker
) : ViewModel() {

    private val _loaded = MutableStateFlow(false)

    /** false, пока список не прочитан: экран не показывает подсказку «список пуст» раньше времени. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val servers: StateFlow<List<TurnServerEntry>> = store.serversFlow
        .catch { e ->
            Log.w(TAG, "Не удалось прочитать список TURN: ${e.javaClass.simpleName}")
            emit(emptyList())
        }
        .onEach { _loaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Target(val id: String, val url: String)

    /** Последний результат по серверу (id -> адрес и статус): перестановка соседей не заставляет бейдж мигать. */
    private val lastResults = ConcurrentHashMap<String, Pair<String, TurnServerStatus>>()

    /**
     * Статусы ВКЛЮЧЁННЫХ серверов по id. Проверка идёт, только пока экран подписан на поток: при открытии,
     * при любом изменении набора/адресов/включённости (flatMapLatest перезапускает проверку), далее раз в
     * [REFRESH_MS]. Выключенные серверы не проверяются и бейджа не получают.
     */
    val statuses: StateFlow<Map<String, TurnServerStatus>> = servers
        .map { list -> list.filter { it.isEnabled }.map { Target(it.id, it.url) }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { targets -> probeLoop(targets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun probeLoop(targets: Set<Target>): Flow<Map<String, TurnServerStatus>> = channelFlow {
        val mutex = Mutex()
        val current = HashMap<String, TurnServerStatus>()
        for (t in targets) {
            lastResults[t.id]?.takeIf { it.first == t.url }?.let { current[t.id] = it.second }
        }
        send(current.toMap())
        if (targets.isEmpty()) return@channelFlow

        suspend fun publish(t: Target, status: TurnServerStatus) = mutex.withLock {
            current[t.id] = status
            send(current.toMap())
        }

        while (true) {
            coroutineScope {
                for (t in targets) launch {
                    val known = mutex.withLock { t.id in current }
                    if (!known) publish(t, TurnServerStatus.CONNECTING)
                    val parsed = TurnUrl.parse(t.url)
                    val result = if (parsed == null) TurnServerStatus.UNREACHABLE else checker.check(parsed)
                    lastResults[t.id] = t.url to result
                    publish(t, result)
                }
            }
            delay(REFRESH_MS)
        }
    }

    private val _message = MutableStateFlow<String?>(null)

    /** Одноразовое сообщение для Snackbar; экран вызывает [onMessageShown] после показа. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onMessageShown() {
        _message.value = null
    }

    fun addServer(url: String, username: String, password: String) =
        launchOp("Не удалось добавить сервер") { store.addServer(url, username, password) }

    fun removeServer(id: String) =
        launchOp("Не удалось удалить сервер") { store.removeServer(id) }

    fun moveUp(id: String) = launchOp("Не удалось изменить порядок") { store.moveUp(id) }

    fun moveDown(id: String) = launchOp("Не удалось изменить порядок") { store.moveDown(id) }

    fun toggleEnabled(id: String) {
        val entry = servers.value.firstOrNull { it.id == id } ?: return
        launchOp("Не удалось сохранить настройку") {
            store.updateServer(entry.copy(isEnabled = !entry.isEnabled), password = null)
        }
    }

    private fun launchOp(errorText: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Только тип исключения: ни адрес, ни логин, ни пароль в лог не попадают.
                Log.w(TAG, "$errorText: ${e.javaClass.simpleName}")
                _message.value = errorText
            }
        }
    }

    private companion object {
        const val TAG = "ramka"
        const val REFRESH_MS = 45_000L
    }
}
