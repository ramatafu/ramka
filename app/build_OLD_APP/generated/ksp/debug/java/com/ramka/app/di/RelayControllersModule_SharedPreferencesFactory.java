package com.ramka.app.di;

import android.content.Context;
import android.content.SharedPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class RelayControllersModule_SharedPreferencesFactory implements Factory<SharedPreferences> {
  private final Provider<Context> ctxProvider;

  public RelayControllersModule_SharedPreferencesFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public SharedPreferences get() {
    return sharedPreferences(ctxProvider.get());
  }

  public static RelayControllersModule_SharedPreferencesFactory create(
      Provider<Context> ctxProvider) {
    return new RelayControllersModule_SharedPreferencesFactory(ctxProvider);
  }

  public static SharedPreferences sharedPreferences(Context ctx) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.sharedPreferences(ctx));
  }
}
