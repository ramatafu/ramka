package com.ramka.app.di;

import com.ramka.domain.relay.RelayChecker;
import com.ramka.network.relay.RelayClient;
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
public final class AppModule_ProvideRelayCheckerFactory implements Factory<RelayChecker> {
  private final Provider<RelayClient> clientProvider;

  public AppModule_ProvideRelayCheckerFactory(Provider<RelayClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public RelayChecker get() {
    return provideRelayChecker(clientProvider.get());
  }

  public static AppModule_ProvideRelayCheckerFactory create(Provider<RelayClient> clientProvider) {
    return new AppModule_ProvideRelayCheckerFactory(clientProvider);
  }

  public static RelayChecker provideRelayChecker(RelayClient client) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRelayChecker(client));
  }
}
