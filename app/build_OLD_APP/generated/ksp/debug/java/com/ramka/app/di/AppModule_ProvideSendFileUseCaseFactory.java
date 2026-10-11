package com.ramka.app.di;

import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.usecase.AttemptDeliveryUseCase;
import com.ramka.domain.usecase.SendFileUseCase;
import com.ramka.domain.util.FileTransferTracker;
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
public final class AppModule_ProvideSendFileUseCaseFactory implements Factory<SendFileUseCase> {
  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<FileTransferTracker> trackerProvider;

  private final Provider<AttemptDeliveryUseCase> attemptDeliveryProvider;

  public AppModule_ProvideSendFileUseCaseFactory(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<FileTransferTracker> trackerProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider) {
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.trackerProvider = trackerProvider;
    this.attemptDeliveryProvider = attemptDeliveryProvider;
  }

  @Override
  public SendFileUseCase get() {
    return provideSendFileUseCase(messageRepositoryProvider.get(), trackerProvider.get(), attemptDeliveryProvider.get());
  }

  public static AppModule_ProvideSendFileUseCaseFactory create(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<FileTransferTracker> trackerProvider,
      Provider<AttemptDeliveryUseCase> attemptDeliveryProvider) {
    return new AppModule_ProvideSendFileUseCaseFactory(messageRepositoryProvider, trackerProvider, attemptDeliveryProvider);
  }

  public static SendFileUseCase provideSendFileUseCase(MessageRepository messageRepository,
      FileTransferTracker tracker, AttemptDeliveryUseCase attemptDelivery) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSendFileUseCase(messageRepository, tracker, attemptDelivery));
  }
}
