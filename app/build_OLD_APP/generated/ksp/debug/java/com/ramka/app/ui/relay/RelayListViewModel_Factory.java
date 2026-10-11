package com.ramka.app.ui.relay;

import com.ramka.app.relay.RelayPoolController;
import com.ramka.app.relay.RelayPoolStore;
import com.ramka.domain.relay.PoolRelayProvider;
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
public final class RelayListViewModel_Factory implements Factory<RelayListViewModel> {
  private final Provider<RelayPoolController> controllerProvider;

  private final Provider<RelayPoolStore> storeProvider;

  private final Provider<PoolRelayProvider> providerProvider;

  public RelayListViewModel_Factory(Provider<RelayPoolController> controllerProvider,
      Provider<RelayPoolStore> storeProvider, Provider<PoolRelayProvider> providerProvider) {
    this.controllerProvider = controllerProvider;
    this.storeProvider = storeProvider;
    this.providerProvider = providerProvider;
  }

  @Override
  public RelayListViewModel get() {
    return newInstance(controllerProvider.get(), storeProvider.get(), providerProvider.get());
  }

  public static RelayListViewModel_Factory create(Provider<RelayPoolController> controllerProvider,
      Provider<RelayPoolStore> storeProvider, Provider<PoolRelayProvider> providerProvider) {
    return new RelayListViewModel_Factory(controllerProvider, storeProvider, providerProvider);
  }

  public static RelayListViewModel newInstance(RelayPoolController controller, RelayPoolStore store,
      PoolRelayProvider provider) {
    return new RelayListViewModel(controller, store, provider);
  }
}
