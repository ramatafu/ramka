package com.ramka.app.di;

import com.ramka.domain.repository.OutboxRepository;
import com.ramka.storage.db.OutboxDao;
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
public final class AppModule_ProvideOutboxRepositoryFactory implements Factory<OutboxRepository> {
  private final Provider<OutboxDao> daoProvider;

  public AppModule_ProvideOutboxRepositoryFactory(Provider<OutboxDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public OutboxRepository get() {
    return provideOutboxRepository(daoProvider.get());
  }

  public static AppModule_ProvideOutboxRepositoryFactory create(Provider<OutboxDao> daoProvider) {
    return new AppModule_ProvideOutboxRepositoryFactory(daoProvider);
  }

  public static OutboxRepository provideOutboxRepository(OutboxDao dao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideOutboxRepository(dao));
  }
}
