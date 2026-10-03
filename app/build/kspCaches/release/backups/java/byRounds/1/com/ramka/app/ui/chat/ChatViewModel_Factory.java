package com.ramka.app.ui.chat;

import androidx.lifecycle.SavedStateHandle;
import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.repository.OutboxRepository;
import com.ramka.domain.repository.TransportRepository;
import com.ramka.domain.usecase.SendReadAckUseCase;
import com.ramka.domain.util.ActiveChatTracker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ChatViewModel_Factory implements Factory<ChatViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<OutboxRepository> outboxRepositoryProvider;

  private final Provider<TransportRepository> transportRepositoryProvider;

  private final Provider<ActiveChatTracker> activeChatProvider;

  private final Provider<SendReadAckUseCase> sendReadAckProvider;

  public ChatViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<OutboxRepository> outboxRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<ActiveChatTracker> activeChatProvider,
      Provider<SendReadAckUseCase> sendReadAckProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.outboxRepositoryProvider = outboxRepositoryProvider;
    this.transportRepositoryProvider = transportRepositoryProvider;
    this.activeChatProvider = activeChatProvider;
    this.sendReadAckProvider = sendReadAckProvider;
  }

  @Override
  public ChatViewModel get() {
    return newInstance(savedStateHandleProvider.get(), contactRepositoryProvider.get(), messageRepositoryProvider.get(), outboxRepositoryProvider.get(), transportRepositoryProvider.get(), activeChatProvider.get(), sendReadAckProvider.get());
  }

  public static ChatViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<OutboxRepository> outboxRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<ActiveChatTracker> activeChatProvider,
      Provider<SendReadAckUseCase> sendReadAckProvider) {
    return new ChatViewModel_Factory(savedStateHandleProvider, contactRepositoryProvider, messageRepositoryProvider, outboxRepositoryProvider, transportRepositoryProvider, activeChatProvider, sendReadAckProvider);
  }

  public static ChatViewModel newInstance(SavedStateHandle savedStateHandle,
      ContactRepository contactRepository, MessageRepository messageRepository,
      OutboxRepository outboxRepository, TransportRepository transportRepository,
      ActiveChatTracker activeChat, SendReadAckUseCase sendReadAck) {
    return new ChatViewModel(savedStateHandle, contactRepository, messageRepository, outboxRepository, transportRepository, activeChat, sendReadAck);
  }
}
