package com.ramka.app.di;

import com.ramka.app.relay.LocalNetworkDetector;
import com.ramka.app.relay.RelayPoolSettings;
import com.ramka.data.repository.LanTransportRepository;
import com.ramka.data.repository.RelayTransportRepository;
import com.ramka.domain.repository.TransportRepository;
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
public final class AppModule_ProvideTransportRepositoryFactory implements Factory<TransportRepository> {
  private final Provider<LanTransportRepository> lanProvider;

  private final Provider<RelayTransportRepository> relayProvider;

  private final Provider<RelayPoolSettings> relayPoolProvider;

  private final Provider<LocalNetworkDetector> localNetworkProvider;

  public AppModule_ProvideTransportRepositoryFactory(Provider<LanTransportRepository> lanProvider,
      Provider<RelayTransportRepository> relayProvider,
      Provider<RelayPoolSettings> relayPoolProvider,
      Provider<LocalNetworkDetector> localNetworkProvider) {
    this.lanProvider = lanProvider;
    this.relayProvider = relayProvider;
    this.relayPoolProvider = relayPoolProvider;
    this.localNetworkProvider = localNetworkProvider;
  }

  @Override
  public TransportRepository get() {
    return provideTransportRepository(lanProvider.get(), relayProvider.get(), relayPoolProvider.get(), localNetworkProvider.get());
  }

  public static AppModule_ProvideTransportRepositoryFactory create(
      Provider<LanTransportRepository> lanProvider,
      Provider<RelayTransportRepository> relayProvider,
      Provider<RelayPoolSettings> relayPoolProvider,
      Provider<LocalNetworkDetector> localNetworkProvider) {
    return new AppModule_ProvideTransportRepositoryFactory(lanProvider, relayProvider, relayPoolProvider, localNetworkProvider);
  }

  public static TransportRepository provideTransportRepository(LanTransportRepository lan,
      RelayTransportRepository relay, RelayPoolSettings relayPool,
      LocalNetworkDetector localNetwork) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTransportRepository(lan, relay, relayPool, localNetwork));
  }
}
