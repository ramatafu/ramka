package com.ramka.app.di

import android.content.Context
import android.util.Log
import com.ramka.app.data.TurnSettingsDataStore
import com.ramka.app.offline.NoiseXOfflineSealer
import com.ramka.app.relay.AndroidLocalNetworkDetector
import com.ramka.app.relay.LocalNetworkDetector
import com.ramka.app.relay.RelayPoolSettings
import com.ramka.app.turn.TurnServerChecker
import com.ramka.app.turn.TurnServerMonitor
import com.ramka.app.yggmail.JavaMailYggmailMail
import com.ramka.app.yggmail.YggmailConfigStore
import com.ramka.app.yggmail.YggmailInbox
import com.ramka.app.yggmail.YggmailMail
import com.ramka.app.yggmail.YggmailTransport
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.securestorage.SecureKeyStore
import com.ramka.data.incoming.IncomingMessageProcessor
import com.ramka.data.repository.CompositeTransportRepository
import com.ramka.data.repository.ContactRepositoryImpl
import com.ramka.data.repository.LanTransportRepository
import com.ramka.data.repository.MessageRepositoryImpl
import com.ramka.data.repository.OutboxRepositoryImpl
import com.ramka.data.repository.RelayTransportRepository
import com.ramka.data.repository.YggdrasilTransportRepository
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayHealthMonitor
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.usecase.AttemptDeliveryUseCase
import com.ramka.domain.usecase.DelayedAckSender
import com.ramka.domain.usecase.FileTransferManager
import com.ramka.domain.usecase.HandleIncomingFileUseCase
import com.ramka.domain.usecase.OfflineFileSignal
import com.ramka.domain.usecase.ProcessOutboxUseCase
import com.ramka.domain.usecase.SendFileUseCase
import com.ramka.domain.usecase.SendMessageUseCase
import com.ramka.domain.usecase.SendReadAckUseCase
import com.ramka.domain.util.ActiveChannelTracker
import com.ramka.domain.util.ChannelSelector
import com.ramka.domain.util.FileTransferTracker
import com.ramka.domain.util.Jitter
import com.ramka.domain.util.OfflinePacketSealer
import com.ramka.domain.util.OfflineSignalTracker
import com.ramka.domain.util.UnavailableOfflineSealer
import com.ramka.domain.util.YggdrasilAddress
import com.ramka.network.call.CallSignaling
import com.ramka.network.local.LanDiscoveryService
import com.ramka.network.local.SecureLanChannel
import com.ramka.network.relay.KeyManagerRelayIdentity
import com.ramka.network.relay.RelayClient
import com.ramka.network.relay.TlsRelayConnector
import com.ramka.network.ygg.YggdrasilNetwork
import com.ramka.storage.db.RamkaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.inject.Singleton

private const val LAN_PORT = 48765
private const val DB_PASSPHRASE_KEY = "db_passphrase"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecureKeyStore(@dagger.hilt.android.qualifiers.ApplicationContext ctx: Context) =
        SecureKeyStore(ctx)

    @Provides
    @Singleton
    fun provideKeyManager(secureKeyStore: SecureKeyStore) = KeyManager(secureKeyStore)

    @Provides
    @Singleton
    fun provideDatabase(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context,
        secureKeyStore: SecureKeyStore
    ): RamkaDatabase {
        var passphrase = secureKeyStore.getBytes(DB_PASSPHRASE_KEY)
        if (passphrase == null) {
            passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            secureKeyStore.putBytes(DB_PASSPHRASE_KEY, passphrase)
        }
        return RamkaDatabase.build(ctx, passphrase)
    }

    @Provides
    fun provideContactDao(db: RamkaDatabase) = db.contactDao()

    @Provides
    fun provideMessageDao(db: RamkaDatabase) = db.messageDao()

    @Provides
    fun provideOutboxDao(db: RamkaDatabase) = db.outboxDao()

    @Provides
    @Singleton
    fun provideContactRepository(dao: com.ramka.storage.db.ContactDao): ContactRepository =
        ContactRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideMessageRepository(dao: com.ramka.storage.db.MessageDao): MessageRepository =
        MessageRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideOutboxRepository(dao: com.ramka.storage.db.OutboxDao): OutboxRepository =
        OutboxRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideActiveChatTracker() = com.ramka.domain.util.ActiveChatTracker()

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, e ->
            Log.e("ramka", "Необработанное исключение в appScope", e)
        }
    )

    @Provides
    @Singleton
    fun provideLanDiscoveryService(@dagger.hilt.android.qualifiers.ApplicationContext ctx: Context) =
        LanDiscoveryService(ctx)

    @Provides
    @Singleton
    fun provideSecureLanChannel(
        keyManager: KeyManager,
        contactRepository: ContactRepository
    ): SecureLanChannel = SecureLanChannel(keyManager, LAN_PORT) { remoteStaticX25519 ->
        contactRepository.observeContacts().first()
            .firstOrNull { it.publicKey.contentEquals(remoteStaticX25519) }
            ?.signingPublicKey
    }

    @Provides
    @Singleton
    fun provideLanTransportRepository(
        secureLanChannel: SecureLanChannel,
        appScope: CoroutineScope
    ): LanTransportRepository = LanTransportRepository(secureLanChannel, appScope)

    // ---- Yggmail (необязательный канал store-and-forward через почту; полностью изолирован) ----

    /**
     * Запечатывание пакетов для офлайн-доставки (store-and-forward): односторонняя схема по мотивам Noise_X
     * (DEVIATIONS.md, O-1…O-6). Открытый текст по почте не уходит никогда: без запечатывания канал не отправляет.
     */
    @Provides
    @Singleton
    fun provideOfflinePacketSealer(keyManager: KeyManager): OfflinePacketSealer = NoiseXOfflineSealer(keyManager)

    @Provides
    @Singleton
    fun provideYggmailMail(): YggmailMail = JavaMailYggmailMail(
        log = { android.util.Log.w("ramka", it) },
        // Подробный обмен с сервером — только при «Проверить» в настройках, пароль скрыт: adb logcat -s ramka-yggmail
        debugLog = { android.util.Log.d("ramka-yggmail", it) }
    )

    @Provides
    @Singleton
    fun provideYggmailInbox(
        configStore: YggmailConfigStore,
        mail: YggmailMail,
        sealer: OfflinePacketSealer,
        prefs: com.ramka.app.preferences.AppPreferences
    ): YggmailInbox = YggmailInbox(configStore, mail, sealer, { prefs.yggmailEnabled }, { android.util.Log.w("ramka", it) })

    @Provides
    @Singleton
    fun provideYggmailTransport(
        configStore: YggmailConfigStore,
        mail: YggmailMail,
        sealer: OfflinePacketSealer,
        inbox: YggmailInbox,
        prefs: com.ramka.app.preferences.AppPreferences
    ): YggmailTransport = YggmailTransport(
        configSource = configStore,
        mail = mail,
        sealer = sealer,
        incoming = inbox.packets,
        enabled = { prefs.yggmailEnabled },
        log = { android.util.Log.w("ramka", it) }
    )

    // ---- Yggdrasil (необязательный канал для контактов с Yggmail-адресом) ----

    /**
     * Отправка по Yggdrasil тем же защищённым каналом, что и LAN. Пробуется, только если включено в настройках
     * и на устройстве есть системный интерфейс Yggdrasil (сама Ramka узел Yggdrasil не содержит).
     */
    @Provides
    @Singleton
    fun provideYggdrasilTransportRepository(
        secureLanChannel: SecureLanChannel,
        prefs: com.ramka.app.preferences.AppPreferences
    ): YggdrasilTransportRepository = YggdrasilTransportRepository(secureLanChannel, LAN_PORT) {
        prefs.yggdrasilEnabled && YggdrasilNetwork.hasActiveInterface()
    }

    /** Какой канал реально использован для каждого контакта в последний раз (индикация в чате). */
    @Provides
    @Singleton
    fun provideActiveChannelTracker() = ActiveChannelTracker()

    /** Прогноз канала для чата: локальная сеть → Yggdrasil (проверка связности) → relay. Отправкой не управляет. */
    @Provides
    @Singleton
    fun provideChannelSelector(
        relayPool: RelayPoolSettings,
        localNetwork: LocalNetworkDetector,
        prefs: com.ramka.app.preferences.AppPreferences,
        yggmail: YggmailTransport
    ): ChannelSelector = ChannelSelector(
        isLocalNetworkAvailable = { contact -> contact.lastKnownAddress is NetworkAddress.Lan && localNetwork.isOnLocalNetwork() },
        isYggdrasilReachable = { contact ->
            val host = YggdrasilAddress.parse(contact.yggmailAddress.orEmpty())
            host != null && YggdrasilNetwork.isReachable(host, LAN_PORT)
        },
        relayAvailable = { relayPool.pool.value.isActive },
        yggdrasilEnabled = { prefs.yggdrasilEnabled && YggdrasilNetwork.hasActiveInterface() },
        yggmailAvailable = { contact -> yggmail.canDeliver(contact) }
    )

    // ---- Домашний relay (этап 3) ----

    /** Пул релеёв с приоритетами, паузами после ошибок и «залипанием»; пул из одного релея — частный случай. */
    @Provides
    @Singleton
    fun providePoolRelayProvider(pool: RelayPoolSettings) = PoolRelayProvider(pool)

    /** Фоновая проверка релеёв раз в 5 минут (запускается из RamkaApp). */
    @Provides
    @Singleton
    fun provideRelayHealthMonitor(
        pool: RelayPoolSettings,
        provider: PoolRelayProvider,
        checker: RelayChecker
    ) = RelayHealthMonitor(pool, provider, checker)

    @Provides
    @Singleton
    fun provideRelayClient(keyManager: KeyManager): RelayClient =
        RelayClient(KeyManagerRelayIdentity(keyManager), TlsRelayConnector())

    @Provides
    fun provideRelayChecker(client: RelayClient): RelayChecker = client

    @Provides
    @Singleton
    fun provideRelayTransportRepository(
        secureLanChannel: SecureLanChannel,
        relayClient: RelayClient,
        relayProvider: PoolRelayProvider
    ) = RelayTransportRepository(secureLanChannel, relayClient, relayProvider)

    @Provides
    @Singleton
    fun provideLocalNetworkDetector(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context
    ): LocalNetworkDetector = AndroidLocalNetworkDetector(ctx)

    /**
     * Единственный TransportRepository для остального приложения (Outbox, ACK, приём):
     * LAN, Yggdrasil и relay выбираются внутри (в таком порядке). Пока relay выключен, а у контакта нет
     * Yggmail-адреса, поведение как до этапа 3.
     */
    @Provides
    @Singleton
    fun provideTransportRepository(
        lan: LanTransportRepository,
        relay: RelayTransportRepository,
        ygg: YggdrasilTransportRepository,
        relayPool: RelayPoolSettings,
        localNetwork: LocalNetworkDetector,
        prefs: com.ramka.app.preferences.AppPreferences,
        activeChannels: ActiveChannelTracker,
        yggmail: YggmailTransport
    ): TransportRepository = CompositeTransportRepository(
        lan = lan,
        relay = relay,
        relayEnabled = { relayPool.pool.value.isActive },
        onLocalNetwork = localNetwork::isOnLocalNetwork,
        ygg = ygg,
        yggdrasilEnabled = { prefs.yggdrasilEnabled },
        onChannelUsed = activeChannels::report,
        yggmail = yggmail,
        yggmailEnabled = { prefs.yggmailEnabled }
    )

    @Provides
    @Singleton
    fun provideIncomingFileStore(store: com.ramka.app.files.AndroidIncomingFileStore): IncomingFileStore = store

    @Provides
    @Singleton
    fun provideHandleIncomingFileUseCase(
        messageRepository: MessageRepository,
        contactRepository: ContactRepository,
        store: IncomingFileStore,
        tracker: FileTransferTracker,
        activeChat: com.ramka.domain.util.ActiveChatTracker
    ) = HandleIncomingFileUseCase(messageRepository, contactRepository, store, tracker) { activeChat.isOpen(it) }

    @Provides
    @Singleton
    fun provideIncomingMessageProcessor(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context,
        contactRepository: ContactRepository,
        messageRepository: MessageRepository,
        transportRepository: TransportRepository,
        activeChat: com.ramka.domain.util.ActiveChatTracker,
        jitter: Jitter,
        handleIncomingFile: HandleIncomingFileUseCase,
        callSignaling: CallSignaling,
        incomingFileStore: com.ramka.app.files.AndroidIncomingFileStore
    ): IncomingMessageProcessor = IncomingMessageProcessor(
        contactRepository, messageRepository, transportRepository, activeChat, jitter,
        handleIncomingFile = handleIncomingFile,
        onCallSignal = callSignaling::onIncoming,
        // Собеседник удалил файловое сообщение у всех — стираем и сам принятый файл.
        onMessageRemoved = { removed ->
            val body = removed.body
            if (body is com.ramka.domain.model.MessageBody.File) {
                com.ramka.app.files.AndroidIncomingFileStore.transferIdFrom(body.uri)
                    ?.let { incomingFileStore.deleteStoredFile(it) }
            }
        }
    ) { saved ->
        val body = saved.body
        if (body is com.ramka.domain.model.MessageBody.Text && body.text == OfflineFileSignal.TEXT) {
            // Собеседник не смог передать нам файл: показываем понятное уведомление вместо общего.
            com.ramka.app.notifications.RamkaNotifications.showFileSignalNotification(ctx)
        } else {
            com.ramka.app.notifications.RamkaNotifications.showNewMessageNotification(ctx)
        }
    }

    /**
     * Сигналинг звонков (этап 7.3): отправка идёт через тот же TransportRepository, приём — через
     * IncomingMessageProcessor (единственный подписчик на входящие). Рабочий цикл живёт в appScope.
     */
    @Provides
    @Singleton
    fun provideCallSignaling(
        contactRepository: ContactRepository,
        transportRepository: TransportRepository,
        appScope: CoroutineScope
    ): CallSignaling = CallSignaling(contactRepository, transportRepository, appScope)

    @Provides
    fun provideAttemptDeliveryUseCase(
        contactRepository: ContactRepository,
        transportRepository: TransportRepository
    ): AttemptDeliveryUseCase = AttemptDeliveryUseCase(contactRepository, transportRepository)

    @Provides
    fun provideProcessOutboxUseCase(
        outboxRepository: OutboxRepository,
        messageRepository: MessageRepository,
        attemptDelivery: AttemptDeliveryUseCase,
        jitter: Jitter
    ): ProcessOutboxUseCase = ProcessOutboxUseCase(outboxRepository, messageRepository, attemptDelivery, jitter)

    /** Живой прогресс передач файлов (этап 6.4): один на всё приложение — его читают и отправитель, и UI. */
    @Provides
    @Singleton
    fun provideFileTransferTracker() = FileTransferTracker()

    @Provides
    @Singleton
    fun provideSendFileUseCase(
        messageRepository: MessageRepository,
        tracker: FileTransferTracker,
        attemptDelivery: AttemptDeliveryUseCase,
        offlineSignal: OfflineFileSignal,
        appScope: CoroutineScope
    ): SendFileUseCase = SendFileUseCase(
        messageRepository,
        tracker,
        { contactId, payload -> attemptDelivery(contactId, payload) },
        // Сигнал уходит в appScope: передача файла завершается сразу, «Повторить» не ждёт сеть.
        onConnectionFailed = { contactId, messageId ->
            appScope.launch { offlineSignal(contactId, messageId) }
        }
    )

    /** Один трекер на процесс: защита от спама сигналом «вам пытаются отправить файл» (в памяти). */
    @Provides
    @Singleton
    fun provideOfflineSignalTracker() = OfflineSignalTracker()

    /** Сигнал идёт обычным текстовым путём (saveOutgoing + Outbox + доставка), не файловым транспортом. */
    @Provides
    @Singleton
    fun provideOfflineFileSignal(
        contactRepository: ContactRepository,
        messageRepository: MessageRepository,
        outboxRepository: OutboxRepository,
        attemptDelivery: AttemptDeliveryUseCase,
        tracker: OfflineSignalTracker
    ): OfflineFileSignal {
        val sendMessage = SendMessageUseCase(contactRepository, messageRepository, outboxRepository, attemptDelivery)
        return OfflineFileSignal(tracker) { contactId, text -> sendMessage(contactId, text) }
    }

    @Provides
    @Singleton
    fun provideFileTransferManager(
        appScope: CoroutineScope,
        sendFile: SendFileUseCase
    ) = FileTransferManager(appScope, sendFile)

    /** Единый источник джиттера (этап 2.5): границы — в PrivacyTimingConfig, по умолчанию ВКЛ. */
    @Provides
    @Singleton
    fun provideJitter(): Jitter = Jitter()

    /** Singleton: один Mutex на все вызовы, чтобы повторный показ чата не дублировал READ-ACK. */
    @Provides
    @Singleton
    fun provideSendReadAckUseCase(
        messageRepository: MessageRepository,
        attemptDelivery: AttemptDeliveryUseCase,
        jitter: Jitter,
        appScope: CoroutineScope
    ): SendReadAckUseCase = SendReadAckUseCase(
        messageRepository,
        appScope,
        DelayedAckSender({ contactId, payload -> attemptDelivery(contactId, payload) }, jitter)::send
    )

    @Provides
    @Singleton
    fun provideAppPreferences(@dagger.hilt.android.qualifiers.ApplicationContext ctx: Context) =
        com.ramka.app.preferences.AppPreferences(ctx)

    @Provides
    @Singleton
    fun provideLanVisibilityController(
        prefs: com.ramka.app.preferences.AppPreferences
    ) = com.ramka.app.discovery.LanVisibilityController(prefs)

    @Provides
    @Singleton
    fun provideBackgroundDeliveryController(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context,
        prefs: com.ramka.app.preferences.AppPreferences
    ) = com.ramka.app.background.BackgroundDeliveryController(
        prefs,
        com.ramka.app.background.WorkManagerSweepScheduler(ctx),
        com.ramka.app.background.ForegroundPersistentService(ctx)
    )

    @Provides
    fun provideLanPort(): Int = LAN_PORT

    // --- НОВОЕ: Глобальный монитор статуса TURN-серверов для индикатора ---
    @Provides
    @Singleton
    fun provideTurnServerMonitor(
        store: TurnSettingsDataStore,
        checker: TurnServerChecker,
        appScope: CoroutineScope
    ): TurnServerMonitor = TurnServerMonitor(store, checker, appScope)
}