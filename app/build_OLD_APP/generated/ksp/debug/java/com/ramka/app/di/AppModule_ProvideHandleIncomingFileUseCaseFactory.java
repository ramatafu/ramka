package com.ramka.app.di;

import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.repository.IncomingFileStore;
import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.usecase.HandleIncomingFileUseCase;
import com.ramka.domain.util.ActiveChatTracker;
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
public final class AppModule_ProvideHandleIncomingFileUseCaseFactory implements Factory<HandleIncomingFileUseCase> {
  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<IncomingFileStore> storeProvider;

  private final Provider<FileTransferTracker> trackerProvider;

  private final Provider<ActiveChatTracker> activeChatProvider;

  public AppModule_ProvideHandleIncomingFileUseCaseFactory(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<IncomingFileStore> storeProvider, Provider<FileTransferTracker> trackerProvider,
      Provider<ActiveChatTracker> activeChatProvider) {
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.storeProvider = storeProvider;
    this.trackerProvider = trackerProvider;
    this.activeChatProvider = activeChatProvider;
  }

  @Override
  public HandleIncomingFileUseCase get() {
    return provideHandleIncomingFileUseCase(messageRepositoryProvider.get(), contactRepositoryProvider.get(), storeProvider.get(), trackerProvider.get(), activeChatProvider.get());
  }

  public static AppModule_ProvideHandleIncomingFileUseCaseFactory create(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<IncomingFileStore> storeProvider, Provider<FileTransferTracker> trackerProvider,
      Provider<ActiveChatTracker> activeChatProvider) {
    return new AppModule_ProvideHandleIncomingFileUseCaseFactory(messageRepositoryProvider, contactRepositoryProvider, storeProvider, trackerProvider, activeChatProvider);
  }

  public static HandleIncomingFileUseCase provideHandleIncomingFileUseCase(
      MessageRepository messageRepository, ContactRepository contactRepository,
      IncomingFileStore store, FileTransferTracker tracker, ActiveChatTracker activeChat) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideHandleIncomingFileUseCase(messageRepository, contactRepository, store, tracker, activeChat));
  }
}
