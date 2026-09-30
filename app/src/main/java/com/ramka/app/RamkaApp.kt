package com.ramka.app

import android.app.Application
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.data.incoming.IncomingMessageProcessor
import com.ramka.domain.usecase.ProcessOutboxUseCase
import com.ramka.network.local.LanDiscoveryService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltAndroidApp
class RamkaApp : Application() {

    @Inject lateinit var lanDiscoveryService: LanDiscoveryService
    @Inject lateinit var incomingMessageProcessor: IncomingMessageProcessor
    @Inject lateinit var processOutboxUseCase: ProcessOutboxUseCase
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var backgroundDeliveryController: BackgroundDeliveryController

    override fun onCreate() {
        super.onCreate()

        com.ramka.app.notifications.RamkaNotifications.ensureChannel(this)

        // Регистрация в локальной сети — вариант B из п. 3.6, дополнение к ручному вводу IP:port.
        // Имя экземпляра — случайное и эфемерное (генерируется заново при каждом запуске
        // процесса, нигде не сохраняется), а НЕ имя устройства/пользователя — чтобы mDNS-имя
        // само по себе не было идентифицирующим или отслеживаемым метаданным.
        val ephemeralInstanceName = "ramka-" + ByteArray(4)
            .also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
        lanDiscoveryService.registerService(port = 48765, instanceName = ephemeralInstanceName)

        // Единственная на всё приложение точка приёма и сохранения входящих сообщений.
        incomingMessageProcessor.start(appScope)

        // ЭТАП B.2, триггер 1/3: любое обнаружение пира в LAN — повод немедленно
        // попробовать разгрести Outbox (не обязательно этот пир — сам факт "сеть
        // изменилась" достаточен, т.к. discoverPeers() не даёт нам identity пира,
        // только host:port; см. LanDiscoveryService.discoverPeers()).
        lanDiscoveryService.discoverPeers()
            .onEach { processOutboxUseCase.processDue(System.currentTimeMillis()) }
            .launchIn(appScope)

        // ЭТАП B.2, триггер 2/3: периодический sweep не чаще раза в 15 минут — если включён
        // тумблер «Фоновая доставка» (ЭТАП B.8; по умолчанию включён, выключен — отменяется).
        // Только планирование WorkManager: СТАРТ foreground-сервиса отсюда запрещён (процесс мог
        // быть поднят в фоне, а на Android 12+ это нельзя) — сервис стартует из MainActivity.onStart.
        backgroundDeliveryController.applyScheduling()

        // Триггер 3/3 (ручное «обновить») — в ContactsViewModel, вызывается по нажатию.

        // Немедленная попытка разгрести всё, что скопилось в Outbox с прошлого
        // запуска (например, сообщения, для которых собеседник был offline при
        // закрытии приложения) — не отдельный "четвёртый триггер" по духу задачи,
        // а просто первый прогон того же механизма сразу при старте.
        appScope.launch { processOutboxUseCase.processDue(System.currentTimeMillis()) }
    }
}
