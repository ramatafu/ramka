package com.ramka.app.di;

import com.ramka.domain.util.ActiveChatTracker;
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
public final class AppModule_ProvideActiveChatTrackerFactory implements Factory<ActiveChatTracker> {
  @Override
  public ActiveChatTracker get() {
    return provideActiveChatTracker();
  }

  public static AppModule_ProvideActiveChatTrackerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ActiveChatTracker provideActiveChatTracker() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideActiveChatTracker());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideActiveChatTrackerFactory INSTANCE = new AppModule_ProvideActiveChatTrackerFactory();
  }
}
