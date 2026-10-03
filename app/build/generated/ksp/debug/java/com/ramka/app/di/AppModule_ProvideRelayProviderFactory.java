package com.ramka.app.di;

import com.ramka.app.relay.RelaySettings;
import com.ramka.domain.relay.SingleRelayProvider;
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
public final class AppModule_ProvideRelayProviderFactory implements Factory<SingleRelayProvider> {
  private final Provider<RelaySettings> settingsProvider;

  public AppModule_ProvideRelayProviderFactory(Provider<RelaySettings> settingsProvider) {
    this.settingsProvider = settingsProvider;
  }

  @Override
  public SingleRelayProvider get() {
    return provideRelayProvider(settingsProvider.get());
  }

  public static AppModule_ProvideRelayProviderFactory create(
      Provider<RelaySettings> settingsProvider) {
    return new AppModule_ProvideRelayProviderFactory(settingsProvider);
  }

  public static SingleRelayProvider provideRelayProvider(RelaySettings settings) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayProvider(settings));
  }
}
