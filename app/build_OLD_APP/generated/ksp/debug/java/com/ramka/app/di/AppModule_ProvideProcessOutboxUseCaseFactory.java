package com.ramka.app.di;

import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.repository.OutboxRepository;
import com.ramka.domain.usecase.AttemptDeliveryUseCase;
import com.ramka.domain.usecase.ProcessOutboxUseCase;
import com.ramka.domain.util.Jitter;
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
public final class AppModule_ProvideProcessOutboxUseCaseFactory implements Factory<ProcessOutboxUseCase> {
  private final Provider<OutboxRepository> outboxRepositoryProvider;

  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<AttemptDeliveryUseCase> attemptDeliveryProvider;

  private final Provider<Jitter> jitterProvider;

  public AppModule_ProvideProcessOutboxUseCaseFactory(
      Provider<OutboxRepository> outboxRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider, Provider<Jitter> jitterProvider) {
    this.outboxRepositoryProvider = outboxRepositoryProvider;
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.attemptDeliveryProvider = attemptDeliveryProvider;
    this.jitterProvider = jitterProvider;
  }

  @Override
  public ProcessOutboxUseCase get() {
    return provideProcessOutboxUseCase(outboxRepositoryProvider.get(), messageRepositoryProvider.get(), attemptDeliveryProvider.get(), jitterProvider.get());
  }

  public static AppModule_ProvideProcessOutboxUseCaseFactory create(
      Provider<OutboxRepository> outboxRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider, Provider<Jitter> jitterProvider) {
    return new AppModule_ProvideProcessOutboxUseCaseFactory(outboxRepositoryProvider, messageRepositoryProvider, attemptDeliveryProvider, jitterProvider);
  }

  public static ProcessOutboxUseCase provideProcessOutboxUseCase(OutboxRepository outboxRepository,
      MessageRepository messageRepository, AttemptDeliveryUseCase attemptDelivery, Jitter jitter) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideProcessOutboxUseCase(outboxRepository, messageRepository, attemptDelivery, jitter));
  }
}
