package com.ramka.app.di;

import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.repository.TransportRepository;
import com.ramka.domain.usecase.AttemptDeliveryUseCase;
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
public final class AppModule_ProvideAttemptDeliveryUseCaseFactory implements Factory<AttemptDeliveryUseCase> {
  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<TransportRepository> transportRepositoryProvider;

  public AppModule_ProvideAttemptDeliveryUseCaseFactory(
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider) {
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.transportRepositoryProvider = transportRepositoryProvider;
  }

  @Override
  public AttemptDeliveryUseCase get() {
    return provideAttemptDeliveryUseCase(contactRepositoryProvider.get(), transportRepositoryProvider.get());
  }

  public static AppModule_ProvideAttemptDeliveryUseCaseFactory create(
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider) {
    return new AppModule_ProvideAttemptDeliveryUseCaseFactory(contactRepositoryProvider, transportRepositoryProvider);
  }

  public static AttemptDeliveryUseCase provideAttemptDeliveryUseCase(
      ContactRepository contactRepository, TransportRepository transportRepository) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAttemptDeliveryUseCase(contactRepository, transportRepository));
  }
}
