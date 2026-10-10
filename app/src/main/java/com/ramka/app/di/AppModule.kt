package com.ramka.app.di

import android.content.Context
import android.util.Log
import com.ramka.app.relay.AndroidLocalNetworkDetector
import com.ramka.app.relay.LocalNetworkDetector
import com.ramka.app.relay.RelayPoolSettings
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.securestorage.SecureKeyStore
import com.ramka.data.incoming.IncomingMessageProcessor
import com.ramka.data.repository.CompositeTransportRepository
import com.ramka.data.repository.ContactRepositoryImpl
import com.ramka.data.repository.LanTransportRepository
import com.ramka.data.repository.MessageRepositoryImpl
import com.ramka.data.repository.OutboxRepositoryImpl
import com.ramka.data.repository.RelayTransportRepository
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayHealthMonitor
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.usecase.AttemptDeliveryUseCase
import com.ramka.domain.usecase.DelayedAckSender
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.usecase.FileTransferManager
import com.ramka.domain.usecase.HandleIncomingFileUseCase
import com.ramka.domain.usecase.SendFileUseCase
import com.ramka.domain.util.FileTransferTracker
import com.ramka.domain.usecase.ProcessOutboxUseCase
import com.ramka.domain.usecase.SendReadAckUseCase
import com.ramka.domain.util.Jitter
import com.ramka.network.local.LanDiscoveryService
import com.ramka.network.local.SecureLanChannel
import com.ramka.network.relay.KeyManagerRelayIdentity
import com.ramka.network.relay.RelayClient
import com.ramka.network.relay.TlsRelayConnector
import com.ramka.storage.db.RamkaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
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
     * LAN и relay выбираются внутри. Пока relay выключен, поведение как до этапа 3.
     */
    @Provides
    @Singleton
    fun provideTransportRepository(
        lan: LanTransportRepository,
        relay: RelayTransportRepository,
        relayPool: RelayPoolSettings,
        localNetwork: LocalNetworkDetector
    ): TransportRepository = CompositeTransportRepository(
        lan = lan,
        relay = relay,
        relayEnabled = { relayPool.pool.value.isActive },
        onLocalNetwork = localNetwork::isOnLocalNetwork
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
        handleIncomingFile: HandleIncomingFileUseCase
    ): IncomingMessageProcessor = IncomingMessageProcessor(
        contactRepository, messageRepository, transportRepository, activeChat, jitter,
        handleIncomingFile = handleIncomingFile
    ) {
        com.ramka.app.notifications.RamkaNotifications.showNewMessageNotification(ctx)
    }

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
        attemptDelivery: AttemptDeliveryUseCase
    ): SendFileUseCase = SendFileUseCase(
        messageRepository,
        tracker,
        { contactId, payload -> attemptDelivery(contactId, payload) }
    )

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
}