package com.ramka.app.di;

import com.ramka.storage.db.ContactDao;
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
public final class AppModule_ProvideContactDaoFactory implements Factory<ContactDao> {
  private final Provider<RamkaDatabase> dbProvider;

  public AppModule_ProvideContactDaoFactory(Provider<RamkaDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ContactDao get() {
    return provideContactDao(dbProvider.get());
  }

  public static AppModule_ProvideContactDaoFactory create(Provider<RamkaDatabase> dbProvider) {
    return new AppModule_ProvideContactDaoFactory(dbProvider);
  }

  public static ContactDao provideContactDao(RamkaDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideContactDao(db));
  }
}
