package com.ramka.app.di;

import com.ramka.domain.repository.TransportRepository;
import com.ramka.network.local.SecureLanChannel;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

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
public final class AppModule_ProvideTransportRepositoryFactory implements Factory<TransportRepository> {
  private final Provider<SecureLanChannel> secureLanChannelProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public AppModule_ProvideTransportRepositoryFactory(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.secureLanChannelProvider = secureLanChannelProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public TransportRepository get() {
    return provideTransportRepository(secureLanChannelProvider.get(), appScopeProvider.get());
  }

  public static AppModule_ProvideTransportRepositoryFactory create(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new AppModule_ProvideTransportRepositoryFactory(secureLanChannelProvider, appScopeProvider);
  }

  public static TransportRepository provideTransportRepository(SecureLanChannel secureLanChannel,
      CoroutineScope appScope) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTransportRepository(secureLanChannel, appScope));
  }
}
