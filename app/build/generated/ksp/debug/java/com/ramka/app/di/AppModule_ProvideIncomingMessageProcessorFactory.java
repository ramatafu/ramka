package com.ramka.app.di;

import android.content.Context;
import com.ramka.data.incoming.IncomingMessageProcessor;
import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.repository.MessageRepository;
import com.ramka.domain.repository.TransportRepository;
import com.ramka.domain.util.ActiveChatTracker;
import com.ramka.domain.util.Jitter;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_ProvideIncomingMessageProcessorFactory implements Factory<IncomingMessageProcessor> {
  private final Provider<Context> ctxProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<TransportRepository> transportRepositoryProvider;

  private final Provider<ActiveChatTracker> activeChatProvider;

  private final Provider<Jitter> jitterProvider;

  public AppModule_ProvideIncomingMessageProcessorFactory(Provider<Context> ctxProvider,
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<ActiveChatTracker> activeChatProvider, Provider<Jitter> jitterProvider) {
    this.ctxProvider = ctxProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.transportRepositoryProvider = transportRepositoryProvider;
    this.activeChatProvider = activeChatProvider;
    this.jitterProvider = jitterProvider;
  }

  @Override
  public IncomingMessageProcessor get() {
    return provideIncomingMessageProcessor(ctxProvider.get(), contactRepositoryProvider.get(), messageRepositoryProvider.get(), transportRepositoryProvider.get(), activeChatProvider.get(), jitterProvider.get());
  }

  public static AppModule_ProvideIncomingMessageProcessorFactory create(
      Provider<Context> ctxProvider, Provider<ContactRepository> contactRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<TransportRepository> transportRepositoryProvider,
      Provider<ActiveChatTracker> activeChatProvider, Provider<Jitter> jitterProvider) {
    return new AppModule_ProvideIncomingMessageProcessorFactory(ctxProvider, contactRepositoryProvider, messageRepositoryProvider, transportRepositoryProvider, activeChatProvider, jitterProvider);
  }

  public static IncomingMessageProcessor provideIncomingMessageProcessor(Context ctx,
      ContactRepository contactRepository, MessageRepository messageRepository,
      TransportRepository transportRepository, ActiveChatTracker activeChat, Jitter jitter) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideIncomingMessageProcessor(ctx, contactRepository, messageRepository, transportRepository, activeChat, jitter));
  }
}
