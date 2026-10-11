package com.ramka.app.di;

import com.ramka.app.relay.RelayPoolSettings;
import com.ramka.app.relay.RelayPoolStore;
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
public final class RelayControllersModule_PoolStoreFactory implements Factory<RelayPoolStore> {
  private final Provider<RelayPoolSettings> settingsProvider;

  public RelayControllersModule_PoolStoreFactory(Provider<RelayPoolSettings> settingsProvider) {
    this.settingsProvider = settingsProvider;
  }

  @Override
  public RelayPoolStore get() {
    return poolStore(settingsProvider.get());
  }

  public static RelayControllersModule_PoolStoreFactory create(
      Provider<RelayPoolSettings> settingsProvider) {
    return new RelayControllersModule_PoolStoreFactory(settingsProvider);
  }

  public static RelayPoolStore poolStore(RelayPoolSettings settings) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.poolStore(settings));
  }
}
