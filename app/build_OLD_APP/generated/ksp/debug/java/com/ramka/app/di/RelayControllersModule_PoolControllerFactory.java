package com.ramka.app.di;

import com.ramka.app.relay.RelayPoolController;
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
public final class RelayControllersModule_PoolControllerFactory implements Factory<RelayPoolController> {
  private final Provider<RelayPoolStore> storeProvider;

  public RelayControllersModule_PoolControllerFactory(Provider<RelayPoolStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public RelayPoolController get() {
    return poolController(storeProvider.get());
  }

  public static RelayControllersModule_PoolControllerFactory create(
      Provider<RelayPoolStore> storeProvider) {
    return new RelayControllersModule_PoolControllerFactory(storeProvider);
  }

  public static RelayPoolController poolController(RelayPoolStore store) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.poolController(store));
  }
}
