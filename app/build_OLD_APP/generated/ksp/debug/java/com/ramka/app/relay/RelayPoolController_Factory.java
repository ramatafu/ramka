package com.ramka.app.relay;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RelayPoolController_Factory implements Factory<RelayPoolController> {
  private final Provider<RelayPoolStore> storeProvider;

  public RelayPoolController_Factory(Provider<RelayPoolStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public RelayPoolController get() {
    return newInstance(storeProvider.get());
  }

  public static RelayPoolController_Factory create(Provider<RelayPoolStore> storeProvider) {
    return new RelayPoolController_Factory(storeProvider);
  }

  public static RelayPoolController newInstance(RelayPoolStore store) {
    return new RelayPoolController(store);
  }
}
