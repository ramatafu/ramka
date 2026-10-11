package com.ramka.app.di;

import com.ramka.app.relay.RelayPoolSettings;
import com.ramka.domain.relay.PoolRelayProvider;
import com.ramka.domain.relay.RelayChecker;
import com.ramka.domain.relay.RelayHealthMonitor;
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
public final class AppModule_ProvideRelayHealthMonitorFactory implements Factory<RelayHealthMonitor> {
  private final Provider<RelayPoolSettings> poolProvider;

  private final Provider<PoolRelayProvider> providerProvider;

  private final Provider<RelayChecker> checkerProvider;

  public AppModule_ProvideRelayHealthMonitorFactory(Provider<RelayPoolSettings> poolProvider,
      Provider<PoolRelayProvider> providerProvider, Provider<RelayChecker> checkerProvider) {
    this.poolProvider = poolProvider;
    this.providerProvider = providerProvider;
    this.checkerProvider = checkerProvider;
  }

  @Override
  public RelayHealthMonitor get() {
    return provideRelayHealthMonitor(poolProvider.get(), providerProvider.get(), checkerProvider.get());
  }

  public static AppModule_ProvideRelayHealthMonitorFactory create(
      Provider<RelayPoolSettings> poolProvider, Provider<PoolRelayProvider> providerProvider,
      Provider<RelayChecker> checkerProvider) {
    return new AppModule_ProvideRelayHealthMonitorFactory(poolProvider, providerProvider, checkerProvider);
  }

  public static RelayHealthMonitor provideRelayHealthMonitor(RelayPoolSettings pool,
      PoolRelayProvider provider, RelayChecker checker) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayHealthMonitor(pool, provider, checker));
  }
}
