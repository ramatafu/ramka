package com.ramka.app.relay;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RelayPoolSettings_Factory implements Factory<RelayPoolSettings> {
  private final Provider<RelayKeyValue> kvProvider;

  private final Provider<RelaySecrets> secretsProvider;

  private final Provider<LegacyRelayReader> legacyProvider;

  public RelayPoolSettings_Factory(Provider<RelayKeyValue> kvProvider,
      Provider<RelaySecrets> secretsProvider, Provider<LegacyRelayReader> legacyProvider) {
    this.kvProvider = kvProvider;
    this.secretsProvider = secretsProvider;
    this.legacyProvider = legacyProvider;
  }

  @Override
  public RelayPoolSettings get() {
    return newInstance(kvProvider.get(), secretsProvider.get(), legacyProvider.get());
  }

  public static RelayPoolSettings_Factory create(Provider<RelayKeyValue> kvProvider,
      Provider<RelaySecrets> secretsProvider, Provider<LegacyRelayReader> legacyProvider) {
    return new RelayPoolSettings_Factory(kvProvider, secretsProvider, legacyProvider);
  }

  public static RelayPoolSettings newInstance(RelayKeyValue kv, RelaySecrets secrets,
      LegacyRelayReader legacy) {
    return new RelayPoolSettings(kv, secrets, legacy);
  }
}
