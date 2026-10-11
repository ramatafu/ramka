package com.ramka.app.di;

import com.ramka.domain.repository.MessageRepository;
import com.ramka.storage.db.MessageDao;
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
public final class AppModule_ProvideMessageRepositoryFactory implements Factory<MessageRepository> {
  private final Provider<MessageDao> daoProvider;

  public AppModule_ProvideMessageRepositoryFactory(Provider<MessageDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public MessageRepository get() {
    return provideMessageRepository(daoProvider.get());
  }

  public static AppModule_ProvideMessageRepositoryFactory create(Provider<MessageDao> daoProvider) {
    return new AppModule_ProvideMessageRepositoryFactory(daoProvider);
  }

  public static MessageRepository provideMessageRepository(MessageDao dao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideMessageRepository(dao));
  }
}
