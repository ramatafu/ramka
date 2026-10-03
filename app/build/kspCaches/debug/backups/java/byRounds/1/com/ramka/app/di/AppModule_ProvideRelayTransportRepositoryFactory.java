package com.ramka.app.di;

import com.ramka.data.repository.RelayTransportRepository;
import com.ramka.domain.relay.SingleRelayProvider;
import com.ramka.network.local.SecureLanChannel;
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
public final class AppModule_ProvideRelayTransportRepositoryFactory implements Factory<RelayTransportRepository> {
  private final Provider<SecureLanChannel> secureLanChannelProvider;

  private final Provider<RelayClient> relayClientProvider;

  private final Provider<SingleRelayProvider> relayProvider;

  public AppModule_ProvideRelayTransportRepositoryFactory(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<RelayClient> relayClientProvider, Provider<SingleRelayProvider> relayProvider) {
    this.secureLanChannelProvider = secureLanChannelProvider;
    this.relayClientProvider = relayClientProvider;
    this.relayProvider = relayProvider;
  }

  @Override
  public RelayTransportRepository get() {
    return provideRelayTransportRepository(secureLanChannelProvider.get(), relayClientProvider.get(), relayProvider.get());
  }

  public static AppModule_ProvideRelayTransportRepositoryFactory create(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<RelayClient> relayClientProvider, Provider<SingleRelayProvider> relayProvider) {
    return new AppModule_ProvideRelayTransportRepositoryFactory(secureLanChannelProvider, relayClientProvider, relayProvider);
  }

  public static RelayTransportRepository provideRelayTransportRepository(
      SecureLanChannel secureLanChannel, RelayClient relayClient,
      SingleRelayProvider relayProvider) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayTransportRepository(secureLanChannel, relayClient, relayProvider));
  }
}
