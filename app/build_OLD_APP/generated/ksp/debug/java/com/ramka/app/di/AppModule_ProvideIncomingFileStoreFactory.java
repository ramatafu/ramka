package com.ramka.app.di;

import com.ramka.app.files.AndroidIncomingFileStore;
import com.ramka.domain.repository.IncomingFileStore;
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
public final class AppModule_ProvideIncomingFileStoreFactory implements Factory<IncomingFileStore> {
  private final Provider<AndroidIncomingFileStore> storeProvider;

  public AppModule_ProvideIncomingFileStoreFactory(
      Provider<AndroidIncomingFileStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public IncomingFileStore get() {
    return provideIncomingFileStore(storeProvider.get());
  }

  public static AppModule_ProvideIncomingFileStoreFactory create(
      Provider<AndroidIncomingFileStore> storeProvider) {
    return new AppModule_ProvideIncomingFileStoreFactory(storeProvider);
  }

  public static IncomingFileStore provideIncomingFileStore(AndroidIncomingFileStore store) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideIncomingFileStore(store));
  }
}
