package com.ramka.app.di;

import com.ramka.domain.util.FileTransferTracker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AppModule_ProvideFileTransferTrackerFactory implements Factory<FileTransferTracker> {
  @Override
  public FileTransferTracker get() {
    return provideFileTransferTracker();
  }

  public static AppModule_ProvideFileTransferTrackerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FileTransferTracker provideFileTransferTracker() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideFileTransferTracker());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideFileTransferTrackerFactory INSTANCE = new AppModule_ProvideFileTransferTrackerFactory();
  }
}
