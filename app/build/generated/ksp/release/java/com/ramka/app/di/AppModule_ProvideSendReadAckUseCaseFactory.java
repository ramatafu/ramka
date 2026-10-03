package com.ramka.app.di;

import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.usecase.AttemptDeliveryUseCase;
import com.ramka.domain.usecase.SendReadAckUseCase;
import com.ramka.domain.util.Jitter;
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
public final class AppModule_ProvideSendReadAckUseCaseFactory implements Factory<SendReadAckUseCase> {
  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<AttemptDeliveryUseCase> attemptDeliveryProvider;

  private final Provider<Jitter> jitterProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public AppModule_ProvideSendReadAckUseCaseFactory(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider, Provider<Jitter> jitterProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.attemptDeliveryProvider = attemptDeliveryProvider;
    this.jitterProvider = jitterProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public SendReadAckUseCase get() {
    return provideSendReadAckUseCase(messageRepositoryProvider.get(), attemptDeliveryProvider.get(), jitterProvider.get(), appScopeProvider.get());
  }

  public static AppModule_ProvideSendReadAckUseCaseFactory create(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider, Provider<Jitter> jitterProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new AppModule_ProvideSendReadAckUseCaseFactory(messageRepositoryProvider, attemptDeliveryProvider, jitterProvider, appScopeProvider);
  }

  public static SendReadAckUseCase provideSendReadAckUseCase(MessageRepository messageRepository,
      AttemptDeliveryUseCase attemptDelivery, Jitter jitter, CoroutineScope appScope) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSendReadAckUseCase(messageRepository, attemptDelivery, jitter, appScope));
  }
}
