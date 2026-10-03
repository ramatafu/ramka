package com.ramka.app.relay

import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayAddressError
import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayConfig
import com.ramka.domain.relay.RelayConfigIssue
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayPinError
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayFormControllerTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)

    private class FakeChecker(var result: RelayCheckResult = RelayCheckResult.Ok) : RelayChecker {
        val calls = ArrayList<RelayEndpoint>()
        override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult {
            calls.add(endpoint)
            return result
        }
    }

    private class Env(initial: RelayConfig? = null, val checker: FakeChecker = FakeChecker()) {
        val prefs = FakeRelayPrefs()
        val settings = RelaySettings(prefs, MemorySecrets()).also { s -> initial?.let { s.save(it) } }
        val controller = RelayFormController(settings, checker)
        val state get() = controller.state.value
    }

    private fun fill(c: RelayFormController, address: String = "Relay.Example.com:4000", token: String = this.token, pin: String = "") {
        c.onAddressChanged(address)
        c.onTokenChanged(token)
        c.onPinChanged(pin)
    }

    @Test
    fun `начальное состояние берётся из сохранённых настроек`() {
        val env = Env(RelayConfig(true, RelayAddress("relay.example.com", 4000), token, pin))
        assertEquals(RelayFormState(true, "relay.example.com:4000", token, pin, active = true), env.state)
        assertEquals(RelayFormState(false, "", "", ""), Env().state)
    }

    @Test
    fun `редактирование помечает форму изменённой и сбрасывает ошибки и результат проверки`() {
        val env = Env()
        env.controller.onEnabledChanged(true)
        env.controller.save() // пустая форма → ошибки
        assertNotNull(env.state.errors)
        env.controller.onAddressChanged("relay.example.com")
        assertTrue(env.state.dirty)
        assertNull(env.state.errors)
        assertEquals(RelayCheckUi.Idle, env.state.check)
    }

    @Test
    fun `включение с корректным несохранённым вводом сохраняет всё и включает релей`() {
        val env = Env()
        fill(env.controller)
        env.controller.onEnabledChanged(true)
        assertTrue(env.state.enabled)
        assertTrue(env.state.active)
        assertFalse(env.state.dirty)
        val saved = env.settings.config.value
        assertEquals(RelayAddress("relay.example.com", 4000), saved.address)
        assertEquals(token, saved.token)
        assertTrue(saved.enabled)
        assertNotNull(saved.activeEndpoint)
    }

    @Test
    fun `включение пустой формы включает релей, но подключения нет — ждём настройки`() {
        val env = Env()
        env.controller.onEnabledChanged(true)
        assertTrue(env.state.enabled)
        assertFalse(env.state.active)
        assertNull(env.state.errors) // ошибки не навязываем, пока пользователь не нажал «Сохранить»
        assertTrue(env.settings.config.value.enabled)
        assertNull(env.settings.config.value.activeEndpoint) // транспорт ничего не получит
    }

    @Test
    fun `включение с некорректным вводом не сохраняет его, подключения нет, ошибки видны после Сохранить`() {
        val env = Env()
        fill(env.controller, address = "bad host", token = "short", pin = "xyz")
        env.controller.onEnabledChanged(true)
        assertTrue(env.state.enabled)
        assertFalse(env.state.active)
        assertNull(env.settings.config.value.address) // некорректное не сохранено
        assertNull(env.settings.config.value.activeEndpoint)

        env.controller.save()
        val e = env.state.errors!!
        assertEquals(RelayAddressError.FORBIDDEN_CHARACTERS, e.address)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, e.token)
        assertEquals(RelayPinError.BAD_FORMAT, e.pin)
    }

    @Test
    fun `IP без пина — Сохранить отклоняет, релей остаётся неактивным`() {
        val env = Env()
        env.controller.onEnabledChanged(true)
        fill(env.controller, address = "203.0.113.7")
        env.controller.save()
        assertEquals(RelayPinError.REQUIRED_FOR_IP, env.state.errors!!.pin)
        assertFalse(env.state.active)
        assertNull(env.settings.config.value.activeEndpoint)
    }

    @Test
    fun `после Сохранить включённый релей становится активным`() {
        val env = Env()
        env.controller.onEnabledChanged(true)
        fill(env.controller)
        assertFalse(env.state.active)
        env.controller.save()
        assertTrue(env.state.active)
        assertNotNull(env.settings.config.value.activeEndpoint)
    }

    @Test
    fun `выключение всегда возможно, прекращает подключения, скрывает статус и не стирает поля`() = runBlocking {
        val env = Env(RelayConfig(true, RelayAddress("relay.example.com", 4000), token, pin))
        env.controller.check()
        assertEquals(RelayCheckUi.Ok, env.state.check)

        env.controller.onEnabledChanged(false)
        assertFalse(env.state.enabled)
        assertFalse(env.state.active)
        assertEquals(RelayCheckUi.Idle, env.state.check)
        assertNull(env.state.errors)
        assertFalse(env.settings.config.value.enabled)
        assertNull(env.settings.config.value.activeEndpoint) // ни приёма, ни отправки через релей
        assertEquals(token, env.settings.config.value.token)
        assertEquals("relay.example.com:4000", env.state.address)
    }

    @Test
    fun `повторное включение возвращает прежние настройки в работу`() {
        val env = Env(RelayConfig(true, RelayAddress("relay.example.com", 4000), token, pin))
        env.controller.onEnabledChanged(false)
        env.controller.onEnabledChanged(true)
        assertTrue(env.state.enabled)
        assertTrue(env.state.active)
        assertNotNull(env.settings.config.value.activeEndpoint)
    }

    @Test
    fun `save нормализует ввод и не меняет переключатель`() {
        val env = Env()
        fill(env.controller, address = "  Relay.Example.COM ", token = "  $token  ", pin = pin.uppercase())
        env.controller.save()
        assertEquals("relay.example.com:48766", env.state.address)
        assertEquals(token, env.state.token)
        assertEquals(pin, env.state.pin)
        assertFalse(env.state.dirty)
        assertFalse(env.state.enabled)
        assertFalse(env.state.active) // сохранено, но релей выключен
        assertFalse(env.settings.config.value.enabled)
        assertEquals(pin, env.settings.config.value.pinSha256)
    }

    @Test
    fun `save с ошибками ничего не сохраняет`() {
        val env = Env(RelayConfig(false, RelayAddress("old.example.com", 1), token, null))
        env.controller.onAddressChanged("bad host")
        env.controller.save()
        assertNotNull(env.state.errors)
        assertEquals(RelayAddress("old.example.com", 1), env.settings.config.value.address)
    }

    @Test
    fun `save при включённом релее применяет новый адрес сразу`() {
        val env = Env(RelayConfig(true, RelayAddress("old.example.com", 1), token, null))
        env.controller.onAddressChanged("new.example.com:2")
        env.controller.save()
        assertEquals(RelayAddress("new.example.com", 2), env.settings.config.value.activeEndpoint?.address)
    }

    @Test
    fun `проверка использует введённые значения без сохранения`() = runBlocking {
        val env = Env()
        env.controller.onEnabledChanged(true)
        fill(env.controller, address = "relay.example.com:4000", pin = pin)
        env.controller.check()
        assertEquals(RelayCheckUi.Ok, env.state.check)
        assertEquals(RelayEndpoint(RelayAddress("relay.example.com", 4000), token, pin), env.checker.calls.single())
        assertNull(env.settings.config.value.address) // ничего не сохранено
    }

    @Test
    fun `проверка показывает причину отказа`() = runBlocking {
        val env = Env(checker = FakeChecker(RelayCheckResult.Failed(RelayFailure.AUTH)))
        env.controller.onEnabledChanged(true)
        fill(env.controller)
        env.controller.check()
        assertEquals(RelayCheckUi.Failed(RelayFailure.AUTH), env.state.check)
    }

    @Test
    fun `проверка с ошибками ввода не обращается к релею`() = runBlocking {
        val env = Env()
        env.controller.onEnabledChanged(true)
        fill(env.controller, address = "")
        env.controller.check()
        assertEquals(RelayAddressError.EMPTY, env.state.errors!!.address)
        assertTrue(env.checker.calls.isEmpty())
        assertEquals(RelayCheckUi.Idle, env.state.check)
    }

    @Test
    fun `при выключенном релее проверка не обращается к серверу`() = runBlocking {
        val env = Env()
        fill(env.controller)
        assertFalse(env.state.enabled)
        env.controller.check()
        assertTrue(env.checker.calls.isEmpty())
        assertEquals(RelayCheckUi.Idle, env.state.check)
    }

    @Test
    fun `результат проверки, пришедший после выключения ползунка, не показывается`() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<RelayCheckResult>()
        val slow = object : RelayChecker {
            override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult = gate.await()
        }
        val prefs = FakeRelayPrefs()
        val settings = RelaySettings(prefs, MemorySecrets())
        val controller = RelayFormController(settings, slow)
        controller.onEnabledChanged(true)
        fill(controller)

        val job = GlobalScope.launch { controller.check() }
        while (controller.state.value.check != RelayCheckUi.Checking) kotlinx.coroutines.delay(5)
        controller.onEnabledChanged(false)
        gate.complete(RelayCheckResult.Ok)
        job.join()

        assertEquals(RelayCheckUi.Idle, controller.state.value.check)
        controller.onEnabledChanged(true)
        assertEquals(RelayCheckUi.Idle, controller.state.value.check) // и не всплывает при повторном включении
    }
}
