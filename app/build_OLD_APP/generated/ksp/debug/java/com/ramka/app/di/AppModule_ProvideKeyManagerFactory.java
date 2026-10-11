package com.ramka.app.di;

import com.ramka.crypto.keys.KeyManager;
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
public final class AppModule_ProvideKeyManagerFactory implements Factory<KeyManager> {
  private final Provider<SecureKeyStore> secureKeyStoreProvider;

  public AppModule_ProvideKeyManagerFactory(Provider<SecureKeyStore> secureKeyStoreProvider) {
    this.secureKeyStoreProvider = secureKeyStoreProvider;
  }

  @Override
  public KeyManager get() {
    return provideKeyManager(secureKeyStoreProvider.get());
  }

  public static AppModule_ProvideKeyManagerFactory create(
      Provider<SecureKeyStore> secureKeyStoreProvider) {
    return new AppModule_ProvideKeyManagerFactory(secureKeyStoreProvider);
  }

  public static KeyManager provideKeyManager(SecureKeyStore secureKeyStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideKeyManager(secureKeyStore));
  }
}
