package com.ramka.app.di;

import com.ramka.app.relay.RelayFormController;
import com.ramka.app.relay.RelaySettings;
import com.ramka.domain.relay.RelayChecker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideRelayFormControllerFactory implements Factory<RelayFormController> {
  private final Provider<RelaySettings> settingsProvider;

  private final Provider<RelayChecker> checkerProvider;

  public AppModule_ProvideRelayFormControllerFactory(Provider<RelaySettings> settingsProvider,
      Provider<RelayChecker> checkerProvider) {
    this.settingsProvider = settingsProvider;
    this.checkerProvider = checkerProvider;
  }

  @Override
  public RelayFormController get() {
    return provideRelayFormController(settingsProvider.get(), checkerProvider.get());
  }

  public static AppModule_ProvideRelayFormControllerFactory create(
      Provider<RelaySettings> settingsProvider, Provider<RelayChecker> checkerProvider) {
    return new AppModule_ProvideRelayFormControllerFactory(settingsProvider, checkerProvider);
  }

  public static RelayFormController provideRelayFormController(RelaySettings settings,
      RelayChecker checker) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayFormController(settings, checker));
  }
}
