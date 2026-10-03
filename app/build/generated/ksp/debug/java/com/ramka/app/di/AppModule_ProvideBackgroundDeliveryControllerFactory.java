package com.ramka.app.di;

import android.content.Context;
import com.ramka.app.background.BackgroundDeliveryController;
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
public final class AppModule_ProvideBackgroundDeliveryControllerFactory implements Factory<BackgroundDeliveryController> {
  private final Provider<Context> ctxProvider;

  private final Provider<AppPreferences> prefsProvider;

  public AppModule_ProvideBackgroundDeliveryControllerFactory(Provider<Context> ctxProvider,
      Provider<AppPreferences> prefsProvider) {
    this.ctxProvider = ctxProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public BackgroundDeliveryController get() {
    return provideBackgroundDeliveryController(ctxProvider.get(), prefsProvider.get());
  }

  public static AppModule_ProvideBackgroundDeliveryControllerFactory create(
      Provider<Context> ctxProvider, Provider<AppPreferences> prefsProvider) {
    return new AppModule_ProvideBackgroundDeliveryControllerFactory(ctxProvider, prefsProvider);
  }

  public static BackgroundDeliveryController provideBackgroundDeliveryController(Context ctx,
      AppPreferences prefs) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBackgroundDeliveryController(ctx, prefs));
  }
}
