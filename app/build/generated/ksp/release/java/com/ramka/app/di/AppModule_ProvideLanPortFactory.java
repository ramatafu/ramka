package com.ramka.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AppModule_ProvideLanPortFactory implements Factory<Integer> {
  @Override
  public Integer get() {
    return provideLanPort();
  }

  public static AppModule_ProvideLanPortFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static int provideLanPort() {
    return AppModule.INSTANCE.provideLanPort();
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideLanPortFactory INSTANCE = new AppModule_ProvideLanPortFactory();
  }
}
