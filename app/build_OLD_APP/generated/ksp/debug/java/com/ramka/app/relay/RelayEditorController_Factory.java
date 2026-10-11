package com.ramka.app.relay;

import com.ramka.domain.relay.RelayChecker;
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
public final class RelayEditorController_Factory implements Factory<RelayEditorController> {
  private final Provider<RelayPoolController> poolControllerProvider;

  private final Provider<RelayChecker> checkerProvider;

  public RelayEditorController_Factory(Provider<RelayPoolController> poolControllerProvider,
      Provider<RelayChecker> checkerProvider) {
    this.poolControllerProvider = poolControllerProvider;
    this.checkerProvider = checkerProvider;
  }

  @Override
  public RelayEditorController get() {
    return newInstance(poolControllerProvider.get(), checkerProvider.get());
  }

  public static RelayEditorController_Factory create(
      Provider<RelayPoolController> poolControllerProvider,
      Provider<RelayChecker> checkerProvider) {
    return new RelayEditorController_Factory(poolControllerProvider, checkerProvider);
  }

  public static RelayEditorController newInstance(RelayPoolController poolController,
      RelayChecker checker) {
    return new RelayEditorController(poolController, checker);
  }
}
