package com.ramka.app.di;

import com.ramka.app.relay.RelayEditorController;
import com.ramka.app.relay.RelayPoolController;
import com.ramka.domain.relay.RelayChecker;
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
public final class RelayControllersModule_EditorControllerFactory implements Factory<RelayEditorController> {
  private final Provider<RelayPoolController> poolProvider;

  private final Provider<RelayChecker> checkerProvider;

  public RelayControllersModule_EditorControllerFactory(Provider<RelayPoolController> poolProvider,
      Provider<RelayChecker> checkerProvider) {
    this.poolProvider = poolProvider;
    this.checkerProvider = checkerProvider;
  }

  @Override
  public RelayEditorController get() {
    return editorController(poolProvider.get(), checkerProvider.get());
  }

  public static RelayControllersModule_EditorControllerFactory create(
      Provider<RelayPoolController> poolProvider, Provider<RelayChecker> checkerProvider) {
    return new RelayControllersModule_EditorControllerFactory(poolProvider, checkerProvider);
  }

  public static RelayEditorController editorController(RelayPoolController pool,
      RelayChecker checker) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.editorController(pool, checker));
  }
}
