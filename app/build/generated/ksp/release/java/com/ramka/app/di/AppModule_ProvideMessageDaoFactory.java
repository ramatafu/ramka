package com.ramka.app.di;

import com.ramka.storage.db.MessageDao;
import com.ramka.storage.db.RamkaDatabase;
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
public final class AppModule_ProvideMessageDaoFactory implements Factory<MessageDao> {
  private final Provider<RamkaDatabase> dbProvider;

  public AppModule_ProvideMessageDaoFactory(Provider<RamkaDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public MessageDao get() {
    return provideMessageDao(dbProvider.get());
  }

  public static AppModule_ProvideMessageDaoFactory create(Provider<RamkaDatabase> dbProvider) {
    return new AppModule_ProvideMessageDaoFactory(dbProvider);
  }

  public static MessageDao provideMessageDao(RamkaDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideMessageDao(db));
  }
}
