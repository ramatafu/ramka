package com.ramka.app.relay;

import com.ramka.crypto.securestorage.SecureKeyStore;
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
public final class KeyStoreSecrets_Factory implements Factory<KeyStoreSecrets> {
  private final Provider<SecureKeyStore> storeProvider;

  public KeyStoreSecrets_Factory(Provider<SecureKeyStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public KeyStoreSecrets get() {
    return newInstance(storeProvider.get());
  }

  public static KeyStoreSecrets_Factory create(Provider<SecureKeyStore> storeProvider) {
    return new KeyStoreSecrets_Factory(storeProvider);
  }

  public static KeyStoreSecrets newInstance(SecureKeyStore store) {
    return new KeyStoreSecrets(store);
  }
}
