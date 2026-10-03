package com.ramka.app.di;

import com.ramka.crypto.keys.KeyManager;
import com.ramka.network.relay.RelayClient;
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
public final class AppModule_ProvideRelayClientFactory implements Factory<RelayClient> {
  private final Provider<KeyManager> keyManagerProvider;

  public AppModule_ProvideRelayClientFactory(Provider<KeyManager> keyManagerProvider) {
    this.keyManagerProvider = keyManagerProvider;
  }

  @Override
  public RelayClient get() {
    return provideRelayClient(keyManagerProvider.get());
  }

  public static AppModule_ProvideRelayClientFactory create(
      Provider<KeyManager> keyManagerProvider) {
    return new AppModule_ProvideRelayClientFactory(keyManagerProvider);
  }

  public static RelayClient provideRelayClient(KeyManager keyManager) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayClient(keyManager));
  }
}
