package com.ramka.app.di;

import com.ramka.crypto.keys.KeyManager;
import com.ramka.domain.repository.ContactRepository;
import com.ramka.network.local.SecureLanChannel;
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
public final class AppModule_ProvideSecureLanChannelFactory implements Factory<SecureLanChannel> {
  private final Provider<KeyManager> keyManagerProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  public AppModule_ProvideSecureLanChannelFactory(Provider<KeyManager> keyManagerProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    this.keyManagerProvider = keyManagerProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
  }

  @Override
  public SecureLanChannel get() {
    return provideSecureLanChannel(keyManagerProvider.get(), contactRepositoryProvider.get());
  }

  public static AppModule_ProvideSecureLanChannelFactory create(
      Provider<KeyManager> keyManagerProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    return new AppModule_ProvideSecureLanChannelFactory(keyManagerProvider, contactRepositoryProvider);
  }

  public static SecureLanChannel provideSecureLanChannel(KeyManager keyManager,
      ContactRepository contactRepository) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSecureLanChannel(keyManager, contactRepository));
  }
}
