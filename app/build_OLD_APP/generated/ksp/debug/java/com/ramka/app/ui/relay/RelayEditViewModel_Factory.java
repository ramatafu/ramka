package com.ramka.app.ui.relay;

import androidx.lifecycle.SavedStateHandle;
import com.ramka.app.relay.RelayEditorController;
import com.ramka.app.relay.RelayPoolController;
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
public final class RelayEditViewModel_Factory implements Factory<RelayEditViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<RelayEditorController> controllerProvider;

  private final Provider<RelayPoolController> poolControllerProvider;

  public RelayEditViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<RelayEditorController> controllerProvider,
      Provider<RelayPoolController> poolControllerProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.controllerProvider = controllerProvider;
    this.poolControllerProvider = poolControllerProvider;
  }

  @Override
  public RelayEditViewModel get() {
    return newInstance(savedStateHandleProvider.get(), controllerProvider.get(), poolControllerProvider.get());
  }

  public static RelayEditViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<RelayEditorController> controllerProvider,
      Provider<RelayPoolController> poolControllerProvider) {
    return new RelayEditViewModel_Factory(savedStateHandleProvider, controllerProvider, poolControllerProvider);
  }

  public static RelayEditViewModel newInstance(SavedStateHandle savedStateHandle,
      RelayEditorController controller, RelayPoolController poolController) {
    return new RelayEditViewModel(savedStateHandle, controller, poolController);
  }
}
