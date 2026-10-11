package com.ramka.app.di;

import com.ramka.app.relay.RelaySecrets;
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
public final class RelayControllersModule_SecretsFactory implements Factory<RelaySecrets> {
  private final Provider<SecureKeyStore> storeProvider;

  public RelayControllersModule_SecretsFactory(Provider<SecureKeyStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public RelaySecrets get() {
    return secrets(storeProvider.get());
  }

  public static RelayControllersModule_SecretsFactory create(
      Provider<SecureKeyStore> storeProvider) {
    return new RelayControllersModule_SecretsFactory(storeProvider);
  }

  public static RelaySecrets secrets(SecureKeyStore store) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.secrets(store));
  }
}
