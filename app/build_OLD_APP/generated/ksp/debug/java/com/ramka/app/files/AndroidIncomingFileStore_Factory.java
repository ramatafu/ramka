package com.ramka.app.files;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AndroidIncomingFileStore_Factory implements Factory<AndroidIncomingFileStore> {
  private final Provider<Context> contextProvider;

  public AndroidIncomingFileStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidIncomingFileStore get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidIncomingFileStore_Factory create(Provider<Context> contextProvider) {
    return new AndroidIncomingFileStore_Factory(contextProvider);
  }

  public static AndroidIncomingFileStore newInstance(Context context) {
    return new AndroidIncomingFileStore(context);
  }
}
