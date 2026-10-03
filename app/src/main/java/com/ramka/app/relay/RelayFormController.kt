package com.ramka.app.relay

import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayConfigDraft
import com.ramka.domain.relay.RelayConfigValidator
import com.ramka.domain.relay.RelayDraftErrors
import com.ramka.domain.relay.RelayDraftResult
import com.ramka.domain.relay.RelayFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Результат кнопки «Проверить подключение» для экрана. */
sealed interface RelayCheckUi {
    object Idle : RelayCheckUi
    object Checking : RelayCheckUi
    object Ok : RelayCheckUi
    data class Failed(val failure: RelayFailure) : RelayCheckUi
}

/**
 * Состояние формы настроек релея.
 * @property enabled значение переключателя (уже сохранено в [RelaySettings]).
 * @property dirty поля изменены и ещё не сохранены.
 * @property active сохранённые настройки полны и релей включён: приложение РЕАЛЬНО подключается к серверу.
 *   Включённый, но ещё не настроенный релей (`enabled && !active`) ничего не делает: ждёт адрес и токен.
 * @property errors ошибки полей после попытки сохранить/проверить; null — ошибок нет.
 */
data class RelayFormState(
    val enabled: Boolean,
    val address: String,
    val token: String,
    val pin: String,
    val dirty: Boolean = false,
    val errors: RelayDraftErrors? = null,
    val check: RelayCheckUi = RelayCheckUi.Idle,
    val active: Boolean = false
)

/**
 * Логика экрана настроек релея без Android-зависимостей (тестируется pure-JVM).
 *
 * Ползунок «Домашний relay» — главный выключатель:
 * - ВЫКЛЮЧЕН: поля и кнопки на экране неактивны, подключений нет (ни приём, ни отправка, ни проверка),
 *   результат проверки скрыт; введённые значения не стираются. [check] в этом состоянии ничего не делает.
 * - ВКЛЮЧЁН: поля редактируются. Включение не требует готовых полей (иначе их негде было бы ввести):
 *   пока адрес и токен не сохранены корректно ([RelayFormState.active] == false), подключений нет.
 *   Если на момент включения в форме уже лежит корректный несохранённый ввод, он сохраняется сразу.
 * - «Проверить подключение» (только при включённом релее) работает с тем, что введено сейчас, без
 *   сохранения: проверка ввода, затем TLS (пин/CA), токен и протокол через [RelayChecker].
 */
class RelayFormController(
    private val settings: RelaySettings,
    private val checker: RelayChecker
) {
    private val form = MutableStateFlow(initial())

    val state: StateFlow<RelayFormState> = form.asStateFlow()

    fun onAddressChanged(value: String) = edit { copy(address = value) }
    fun onTokenChanged(value: String) = edit { copy(token = value) }
    fun onPinChanged(value: String) = edit { copy(pin = value) }

    fun onEnabledChanged(enabled: Boolean) {
        if (!enabled) {
            settings.setEnabled(false)
            form.update { it.copy(enabled = false, errors = null, check = RelayCheckUi.Idle, active = false) }
            return
        }
        // Корректный несохранённый ввод сохраняем вместе с включением; иначе только включаем.
        val pending = if (form.value.dirty) validate(enabled = true) else null
        if (pending is RelayDraftResult.Valid) {
            settings.save(pending.config)
            form.update { it.copy(enabled = true, dirty = false, errors = null, active = isActive()) }
        } else {
            settings.setEnabled(true)
            form.update { it.copy(enabled = true, active = isActive()) }
        }
    }

    /** Сохраняет введённое (переключатель не меняется). */
    fun save() {
        when (val result = validate(enabled = form.value.enabled)) {
            is RelayDraftResult.Valid -> {
                settings.save(result.config)
                // Показываем нормализованные значения (регистр домена, формат пина, обрезанный токен).
                form.update {
                    it.copy(
                        address = result.config.address?.format().orEmpty(),
                        token = result.config.token,
                        pin = result.config.pinSha256.orEmpty(),
                        dirty = false,
                        errors = null,
                        active = isActive()
                    )
                }
            }
            is RelayDraftResult.Invalid -> form.update { it.copy(errors = result.errors) }
        }
    }

    /** Проверяет подключение с введёнными значениями, ничего не сохраняя. Только при включённом релее. */
    suspend fun check() {
        if (!form.value.enabled) return // релей выключен — к серверу не обращаемся
        val result = validate(enabled = form.value.enabled)
        val endpoint = (result as? RelayDraftResult.Valid)?.config?.endpointOrNull
        if (endpoint == null) {
            form.update { it.copy(errors = (result as? RelayDraftResult.Invalid)?.errors, check = RelayCheckUi.Idle) }
            return
        }
        form.update { it.copy(errors = null, check = RelayCheckUi.Checking) }
        val outcome = checker.check(endpoint)
        form.update {
            // Пока шла проверка, ползунок могли выключить: тогда результат не показываем.
            if (!it.enabled) it else it.copy(
                check = when (outcome) {
                    RelayCheckResult.Ok -> RelayCheckUi.Ok
                    is RelayCheckResult.Failed -> RelayCheckUi.Failed(outcome.failure)
                }
            )
        }
    }

    private fun isActive(): Boolean = settings.config.value.activeEndpoint != null

    private fun validate(enabled: Boolean): RelayDraftResult {
        val f = form.value
        return RelayConfigValidator.validate(RelayConfigDraft(f.address, f.token, f.pin), enabled)
    }

    private fun edit(change: RelayFormState.() -> RelayFormState) {
        form.update { it.change().copy(dirty = true, errors = null, check = RelayCheckUi.Idle) }
    }

    private fun initial(): RelayFormState {
        val c = settings.config.value
        return RelayFormState(
            enabled = c.enabled,
            address = c.address?.format().orEmpty(),
            token = c.token,
            pin = c.pinSha256.orEmpty(),
            active = c.activeEndpoint != null
        )
    }
}
