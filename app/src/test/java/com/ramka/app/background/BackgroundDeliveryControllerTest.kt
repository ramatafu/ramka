package com.ramka.app.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDeliveryControllerTest {

    private class FakePrefs(
        override var backgroundDeliveryEnabled: Boolean = true,   // как в AppPreferences: по умолчанию ВКЛ
        override var persistentServiceEnabled: Boolean = false,   // по умолчанию ВЫКЛ
        override var backgroundHintShown: Boolean = false
    ) : BackgroundDeliveryPrefs

    private class FakeScheduler : SweepScheduler {
        val calls = mutableListOf<String>()
        var scheduled = false
        override fun schedule() { calls += "schedule"; scheduled = true }
        override fun cancel() { calls += "cancel"; scheduled = false }
    }

    private class FakeService : PersistentService {
        val calls = mutableListOf<String>()
        var running = false
        override fun start() { calls += "start"; running = true }
        override fun stop() { calls += "stop"; running = false }
    }

    private fun setup(prefs: FakePrefs = FakePrefs()): Triple<BackgroundDeliveryController, FakeScheduler, FakeService> {
        val scheduler = FakeScheduler()
        val service = FakeService()
        return Triple(BackgroundDeliveryController(prefs, scheduler, service), scheduler, service)
    }

    @Test
    fun `defaults - worker scheduled and service not running`() {
        val (controller, scheduler, service) = setup()
        controller.applyScheduling()
        controller.applyService()
        assertTrue(scheduler.scheduled)
        assertFalse(service.running)
    }

    @Test
    fun `turning background delivery off cancels the worker and stops the service`() {
        val prefs = FakePrefs(persistentServiceEnabled = true)
        val (controller, scheduler, service) = setup(prefs)
        controller.applyScheduling(); controller.applyService()
        assertTrue(scheduler.scheduled); assertTrue(service.running)

        controller.setBackgroundDelivery(false)

        assertFalse(prefs.backgroundDeliveryEnabled)
        assertFalse(scheduler.scheduled)
        assertFalse(service.running)
    }

    @Test
    fun `turning background delivery back on restores worker and previously chosen service`() {
        val prefs = FakePrefs(persistentServiceEnabled = true)
        val (controller, scheduler, service) = setup(prefs)

        controller.setBackgroundDelivery(false)
        controller.setBackgroundDelivery(true)

        assertTrue(prefs.persistentServiceEnabled) // сохранённый выбор не стёрт
        assertTrue(scheduler.scheduled)
        assertTrue(service.running)
    }

    @Test
    fun `enabling the persistent service starts it only when background delivery is on`() {
        val prefs = FakePrefs()
        val (controller, _, service) = setup(prefs)

        controller.setPersistentService(true)
        assertTrue(service.running)

        controller.setPersistentService(false)
        assertFalse(service.running)
    }

    @Test
    fun `persistent service cannot run while background delivery is off`() {
        val prefs = FakePrefs(backgroundDeliveryEnabled = false)
        val (controller, _, service) = setup(prefs)

        controller.setPersistentService(true)

        assertTrue("выбор запоминается", prefs.persistentServiceEnabled)
        assertFalse("но сервис не запускается без главного тумблера", service.running)
    }

    @Test
    fun `applyScheduling never touches the service - safe to call from Application`() {
        val (controller, _, service) = setup(FakePrefs(persistentServiceEnabled = true))
        controller.applyScheduling()
        assertEquals(emptyList<String>(), service.calls)
    }

    @Test
    fun `hint is shown once when background delivery is on`() {
        val prefs = FakePrefs()
        val (controller, _, _) = setup(prefs)

        assertTrue(controller.shouldShowHint())
        controller.markHintShown()
        assertFalse(controller.shouldShowHint())
        assertTrue(prefs.backgroundHintShown)
    }

    @Test
    fun `hint is not shown while background delivery is off`() {
        val (controller, _, _) = setup(FakePrefs(backgroundDeliveryEnabled = false))
        assertFalse(controller.shouldShowHint())
    }

    @Test
    fun `toggling off and on again does not re-show an already shown hint`() {
        val (controller, _, _) = setup()
        controller.markHintShown()
        controller.setBackgroundDelivery(false)
        controller.setBackgroundDelivery(true)
        assertFalse(controller.shouldShowHint())
    }
}
