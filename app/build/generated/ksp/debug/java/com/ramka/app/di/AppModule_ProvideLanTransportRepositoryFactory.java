package com.ramka.app.di;

import com.ramka.data.repository.LanTransportRepository;
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
public final class AppModule_ProvideLanTransportRepositoryFactory implements Factory<LanTransportRepository> {
  private final Provider<SecureLanChannel> secureLanChannelProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public AppModule_ProvideLanTransportRepositoryFactory(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.secureLanChannelProvider = secureLanChannelProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public LanTransportRepository get() {
    return provideLanTransportRepository(secureLanChannelProvider.get(), appScopeProvider.get());
  }

  public static AppModule_ProvideLanTransportRepositoryFactory create(
      Provider<SecureLanChannel> secureLanChannelProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new AppModule_ProvideLanTransportRepositoryFactory(secureLanChannelProvider, appScopeProvider);
  }

  public static LanTransportRepository provideLanTransportRepository(
      SecureLanChannel secureLanChannel, CoroutineScope appScope) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLanTransportRepository(secureLanChannel, appScope));
  }
}
