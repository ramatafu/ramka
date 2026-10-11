package com.ramka.app.di;

import android.content.Context;
import com.ramka.app.preferences.AppPreferences;
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
public final class AppModule_ProvideAppPreferencesFactory implements Factory<AppPreferences> {
  private final Provider<Context> ctxProvider;

  public AppModule_ProvideAppPreferencesFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public AppPreferences get() {
    return provideAppPreferences(ctxProvider.get());
  }

  public static AppModule_ProvideAppPreferencesFactory create(Provider<Context> ctxProvider) {
    return new AppModule_ProvideAppPreferencesFactory(ctxProvider);
  }

  public static AppPreferences provideAppPreferences(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAppPreferences(ctx));
  }
}
