package com.ramka.app.di

import android.content.Context
import android.content.SharedPreferences
import com.ramka.app.preferences.AppPreferences
import com.ramka.app.relay.AppPreferencesLegacyReader
import com.ramka.app.relay.KeyStoreSecrets
import com.ramka.app.relay.LegacyRelayReader
import com.ramka.app.relay.PrefsKeyValue
import com.ramka.app.relay.RelayEditorController
import com.ramka.app.relay.RelayKeyValue
import com.ramka.app.relay.RelayPoolController
import com.ramka.app.relay.RelayPoolSettings
import com.ramka.app.relay.RelayPoolStore
import com.ramka.app.relay.RelaySecrets
import com.ramka.crypto.securestorage.SecureKeyStore
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RelayControllersModule {

    @Provides @Singleton
    fun sharedPreferences(@ApplicationContext ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ramka_relay_pool", Context.MODE_PRIVATE)

    @Provides @Singleton
    fun keyValue(prefs: SharedPreferences): RelayKeyValue = PrefsKeyValue(prefs)

    @Provides @Singleton
    fun secrets(store: SecureKeyStore): RelaySecrets = KeyStoreSecrets(store)

    @Provides @Singleton
    fun legacyReader(prefs: AppPreferences, secrets: RelaySecrets): LegacyRelayReader =
        AppPreferencesLegacyReader(prefs, secrets)

    @Provides @Singleton
    fun poolStore(settings: RelayPoolSettings): RelayPoolStore = settings

    @Provides @Singleton
    fun poolController(store: RelayPoolStore): RelayPoolController = RelayPoolController(store)

    @Provides @Singleton
    fun editorController(pool: RelayPoolController, checker: RelayChecker): RelayEditorController =
        RelayEditorController(pool, checker)

    @Provides @Singleton
    fun relayClock(): RelayClock = object : RelayClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }
}