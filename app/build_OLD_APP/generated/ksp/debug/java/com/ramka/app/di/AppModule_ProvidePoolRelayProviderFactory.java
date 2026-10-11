package com.ramka.app.di;

import com.ramka.app.relay.RelayPoolSettings;
import com.ramka.domain.relay.PoolRelayProvider;
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
public final class AppModule_ProvidePoolRelayProviderFactory implements Factory<PoolRelayProvider> {
  private final Provider<RelayPoolSettings> poolProvider;

  public AppModule_ProvidePoolRelayProviderFactory(Provider<RelayPoolSettings> poolProvider) {
    this.poolProvider = poolProvider;
  }

  @Override
  public PoolRelayProvider get() {
    return providePoolRelayProvider(poolProvider.get());
  }

  public static AppModule_ProvidePoolRelayProviderFactory create(
      Provider<RelayPoolSettings> poolProvider) {
    return new AppModule_ProvidePoolRelayProviderFactory(poolProvider);
  }

  public static PoolRelayProvider providePoolRelayProvider(RelayPoolSettings pool) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePoolRelayProvider(pool));
  }
}
