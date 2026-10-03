package com.ramka.app.di;

import com.ramka.app.preferences.AppPreferences;
import com.ramka.app.relay.RelaySettings;
import com.ramka.crypto.securestorage.SecureKeyStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AppModule_ProvideRelaySettingsFactory implements Factory<RelaySettings> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<SecureKeyStore> secureKeyStoreProvider;

  public AppModule_ProvideRelaySettingsFactory(Provider<AppPreferences> prefsProvider,
      Provider<SecureKeyStore> secureKeyStoreProvider) {
    this.prefsProvider = prefsProvider;
    this.secureKeyStoreProvider = secureKeyStoreProvider;
  }

  @Override
  public RelaySettings get() {
    return provideRelaySettings(prefsProvider.get(), secureKeyStoreProvider.get());
  }

  public static AppModule_ProvideRelaySettingsFactory create(Provider<AppPreferences> prefsProvider,
      Provider<SecureKeyStore> secureKeyStoreProvider) {
    return new AppModule_ProvideRelaySettingsFactory(prefsProvider, secureKeyStoreProvider);
  }

  public static RelaySettings provideRelaySettings(AppPreferences prefs,
      SecureKeyStore secureKeyStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelaySettings(prefs, secureKeyStore));
  }
}
