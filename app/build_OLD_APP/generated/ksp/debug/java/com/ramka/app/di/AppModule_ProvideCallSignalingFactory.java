package com.ramka.app.di;

import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.repository.TransportRepository;
import com.ramka.network.call.CallSignaling;
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
public final class AppModule_ProvideCallSignalingFactory implements Factory<CallSignaling> {
  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<TransportRepository> transportRepositoryProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public AppModule_ProvideCallSignalingFactory(
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.transportRepositoryProvider = transportRepositoryProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public CallSignaling get() {
    return provideCallSignaling(contactRepositoryProvider.get(), transportRepositoryProvider.get(), appScopeProvider.get());
  }

  public static AppModule_ProvideCallSignalingFactory create(
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new AppModule_ProvideCallSignalingFactory(contactRepositoryProvider, transportRepositoryProvider, appScopeProvider);
  }

  public static CallSignaling provideCallSignaling(ContactRepository contactRepository,
      TransportRepository transportRepository, CoroutineScope appScope) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCallSignaling(contactRepository, transportRepository, appScope));
  }
}
