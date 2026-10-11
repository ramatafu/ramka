package com.ramka.app.di;

import com.ramka.app.preferences.AppPreferences;
import com.ramka.app.relay.LegacyRelayReader;
import com.ramka.app.relay.RelaySecrets;
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
public final class RelayControllersModule_LegacyReaderFactory implements Factory<LegacyRelayReader> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<RelaySecrets> secretsProvider;

  public RelayControllersModule_LegacyReaderFactory(Provider<AppPreferences> prefsProvider,
      Provider<RelaySecrets> secretsProvider) {
    this.prefsProvider = prefsProvider;
    this.secretsProvider = secretsProvider;
  }

  @Override
  public LegacyRelayReader get() {
    return legacyReader(prefsProvider.get(), secretsProvider.get());
  }

  public static RelayControllersModule_LegacyReaderFactory create(
      Provider<AppPreferences> prefsProvider, Provider<RelaySecrets> secretsProvider) {
    return new RelayControllersModule_LegacyReaderFactory(prefsProvider, secretsProvider);
  }

  public static LegacyRelayReader legacyReader(AppPreferences prefs, RelaySecrets secrets) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.legacyReader(prefs, secrets));
  }
}
