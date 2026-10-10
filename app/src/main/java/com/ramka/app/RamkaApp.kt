package com.ramka.app

import android.app.Application
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.discovery.LanPresenceCoordinator
import com.ramka.app.discovery.LanVisibilityController
import com.ramka.data.incoming.IncomingMessageProcessor
import android.util.Log
import com.ramka.domain.relay.RelayHealthMonitor
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.usecase.ProcessOutboxUseCase
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
    @Inject lateinit var incomingFileStore: IncomingFileStore

    override fun onCreate() {
        super.onCreate()

        com.ramka.app.notifications.RamkaNotifications.ensureChannel(this)

        // Единственная на всё приложение точка приёма и сохранения входящих сообщений.
        // Сначала помечаем FAILED файловые передачи, оставшиеся в SENDING от прошлого процесса
        // (этап 6.4), и только потом запускаем приём — иначе пометка могла бы задеть передачу,
        // начавшуюся уже в этом процессе. Сбой пометки приём не блокирует.
        appScope.launch {
            try {
                messageRepository.failInterruptedFileTransfers()
                incomingFileStore.discardAll() // осиротевшие временные файлы прерванных приёмов
            } catch (e: Exception) {
                Log.w("ramka", "Не удалось пометить прерванные передачи: ${e.javaClass.simpleName}")
            }
            incomingMessageProcessor.start(appScope)
        }

        // mDNS (вариант B из п. 3.6, дополнение к ручному вводу IP:port) — только пока включён
        // тумблер в настройках (этап 2.5, по умолчанию ВКЛ). Включено: сервис вещается под
        // случайным эфемерным именем (новое при каждом включении, не имя устройства/пользователя)
        // и ищутся другие экземпляры. Выключено: ни объявлений, ни запросов; приём входящих
        // (TCP-слушатель), sweep и ручной коннект работают как раньше.
        //
        // ЭТАП B.2, триггер 1/3: любое обнаружение пира в LAN — повод немедленно попробовать
        // разгрести Outbox (не обязательно этот пир — сам факт "сеть изменилась" достаточен,
        // т.к. discoverPeers() не даёт нам identity пира, только host:port).
        LanPresenceCoordinator(
            enabled = lanVisibilityController.enabled,
            register = { name -> lanDiscoveryService.registerService(port = 48765, instanceName = name) },
            unregister = { lanDiscoveryService.unregisterService() },
            discoverPeers = { lanDiscoveryService.discoverPeers() },
            onPeerDiscovered = { processOutboxUseCase.processDue(System.currentTimeMillis()) }
        ).start(appScope)

        // ЭТАП B.2, триггер 2/3: периодический sweep не чаще раза в 15 минут — если включён
        // тумблер «Фоновая доставка» (ЭТАП B.8; по умолчанию включён, выключен — отменяется).
        // Только планирование WorkManager: СТАРТ foreground-сервиса отсюда запрещён (процесс мог
        // быть поднят в фоне, а на Android 12+ это нельзя) — сервис стартует из MainActivity.onStart.
        backgroundDeliveryController.applyScheduling()

        // Фоновая проверка релеёв (этап 3, пул): только пока пул релеёв включён и в нём есть рабочие записи —
        // сразу при включении и далее раз в 5 минут (в режиме экономии батареи реже). Выключен — ни одного обращения.
        relayHealthMonitor.start(appScope)

        // Триггер 3/3 (ручное «обновить») — в ContactsViewModel, вызывается по нажатию.

        // Немедленная попытка разгрести всё, что скопилось в Outbox с прошлого
        // запуска (например, сообщения, для которых собеседник был offline при
        // закрытии приложения) — не отдельный "четвёртый триггер" по духу задачи,
        // а просто первый прогон того же механизма сразу при старте.
        appScope.launch { processOutboxUseCase.processDue(System.currentTimeMillis()) }
    }
}
