package com.ramka.app.relay;

import com.ramka.app.preferences.AppPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppPreferencesLegacyReader_Factory implements Factory<AppPreferencesLegacyReader> {
  private final Provider<AppPreferences> prefsProvider;

  private final Provider<RelaySecrets> secretsProvider;

  public AppPreferencesLegacyReader_Factory(Provider<AppPreferences> prefsProvider,
      Provider<RelaySecrets> secretsProvider) {
    this.prefsProvider = prefsProvider;
    this.secretsProvider = secretsProvider;
  }

  @Override
  public AppPreferencesLegacyReader get() {
    return newInstance(prefsProvider.get(), secretsProvider.get());
  }

  public static AppPreferencesLegacyReader_Factory create(Provider<AppPreferences> prefsProvider,
      Provider<RelaySecrets> secretsProvider) {
    return new AppPreferencesLegacyReader_Factory(prefsProvider, secretsProvider);
  }

  public static AppPreferencesLegacyReader newInstance(AppPreferences prefs, RelaySecrets secrets) {
    return new AppPreferencesLegacyReader(prefs, secrets);
  }
}
