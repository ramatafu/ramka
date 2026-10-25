package com.ramka.app

import android.app.Application
import android.util.Log
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.discovery.LanPresenceCoordinator
import com.ramka.app.discovery.LanVisibilityController
import com.ramka.app.panic.PanicWipe
import com.ramka.data.incoming.IncomingMessageProcessor
import com.ramka.domain.relay.RelayHealthMonitor
import com.ramka.app.call.CallAlerts
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.usecase.ProcessOutboxUseCase
import com.ramka.network.local.DiscoveredPeer
import com.ramka.network.local.LanDiscoveryService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RamkaApp : Application() {

    @Inject lateinit var lanDiscoveryService: LanDiscoveryService
    @Inject lateinit var incomingMessageProcessor: IncomingMessageProcessor
    @Inject lateinit var processOutboxUseCase: ProcessOutboxUseCase
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var backgroundDeliveryController: BackgroundDeliveryController
    @Inject lateinit var lanVisibilityController: LanVisibilityController
    @Inject lateinit var relayHealthMonitor: RelayHealthMonitor
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var contactRepository: ContactRepository
    @Inject lateinit var callAlerts: CallAlerts
    @Inject lateinit var incomingFileStore: IncomingFileStore

    override fun onCreate() {
        try {
            if (PanicWipe.isPending(this)) {
                PanicWipe.wipeAll(this)
                PanicWipe.clearMarker(this)
            }
        } catch (_: Throwable) { }

        super.onCreate()

        com.ramka.app.notifications.RamkaNotifications.ensureChannel(this)

        // Входящие звонки, пока экрана нет: рингтон, вибрация, баннер (см. CallAlerts).
        callAlerts.start()

        appScope.launch {
            try {
                // «Избранное» создаётся один раз (идемпотентно); после «кнопки паники» БД пуста — появится заново, пустым.
                contactRepository.getOrCreateSelfContact()
            } catch (e: Exception) {
                Log.w("ramka", "Не удалось создать «Избранное»: ${e.javaClass.simpleName}")
            }
            try {
                messageRepository.failInterruptedFileTransfers()
                incomingFileStore.discardAll()
            } catch (e: Exception) {
                Log.w("ramka", "Не удалось пометить прерванные передачи: ${e.javaClass.simpleName}")
            }
            incomingMessageProcessor.start(appScope)
        }

        // mDNS обнаружение
        LanPresenceCoordinator(
            enabled = lanVisibilityController.enabled,
            register = { name -> lanDiscoveryService.registerService(port = 48765, instanceName = name) },
            unregister = { lanDiscoveryService.unregisterService() },
            discoverPeers = { lanDiscoveryService.discoverPeers() },
            onPeerDiscovered = { peer ->
                Log.d("ramka", "LAN: Обнаружен пир! IP: ${peer.host}, Порт: ${peer.port}")
                
                // Сообщаем контроллеру, что пир найден (это зажжет зеленый логотип)
                lanVisibilityController.notifyPeerDiscovered(peer)

                // Запускаем обработку очереди сообщений
                appScope.launch { 
                    processOutboxUseCase.processDue(System.currentTimeMillis()) 
                }
            }
        ).start(appScope)

        backgroundDeliveryController.applyScheduling()
        relayHealthMonitor.start(appScope)
        appScope.launch { processOutboxUseCase.processDue(System.currentTimeMillis()) }
    }
}