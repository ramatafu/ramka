package com.ramka.app.di;

import com.ramka.domain.util.Jitter;
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
public final class AppModule_ProvideJitterFactory implements Factory<Jitter> {
  @Override
  public Jitter get() {
    return provideJitter();
  }

  public static AppModule_ProvideJitterFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Jitter provideJitter() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideJitter());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideJitterFactory INSTANCE = new AppModule_ProvideJitterFactory();
  }
}
