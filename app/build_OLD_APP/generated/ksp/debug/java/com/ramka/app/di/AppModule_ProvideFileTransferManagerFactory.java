package com.ramka.app.di;

import com.ramka.domain.usecase.FileTransferManager;
import com.ramka.domain.usecase.SendFileUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

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
public final class AppModule_ProvideFileTransferManagerFactory implements Factory<FileTransferManager> {
  private final Provider<CoroutineScope> appScopeProvider;

  private final Provider<SendFileUseCase> sendFileProvider;

  public AppModule_ProvideFileTransferManagerFactory(Provider<CoroutineScope> appScopeProvider,
      Provider<SendFileUseCase> sendFileProvider) {
    this.appScopeProvider = appScopeProvider;
    this.sendFileProvider = sendFileProvider;
  }

  @Override
  public FileTransferManager get() {
    return provideFileTransferManager(appScopeProvider.get(), sendFileProvider.get());
  }

  public static AppModule_ProvideFileTransferManagerFactory create(
      Provider<CoroutineScope> appScopeProvider, Provider<SendFileUseCase> sendFileProvider) {
    return new AppModule_ProvideFileTransferManagerFactory(appScopeProvider, sendFileProvider);
  }

  public static FileTransferManager provideFileTransferManager(CoroutineScope appScope,
      SendFileUseCase sendFile) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideFileTransferManager(appScope, sendFile));
  }
}
