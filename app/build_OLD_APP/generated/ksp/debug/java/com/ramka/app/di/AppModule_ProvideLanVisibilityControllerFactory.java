package com.ramka.app.di;

import com.ramka.app.discovery.LanVisibilityController;
import com.ramka.app.preferences.AppPreferences;
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
public final class AppModule_ProvideLanVisibilityControllerFactory implements Factory<LanVisibilityController> {
  private final Provider<AppPreferences> prefsProvider;

  public AppModule_ProvideLanVisibilityControllerFactory(Provider<AppPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public LanVisibilityController get() {
    return provideLanVisibilityController(prefsProvider.get());
  }

  public static AppModule_ProvideLanVisibilityControllerFactory create(
      Provider<AppPreferences> prefsProvider) {
    return new AppModule_ProvideLanVisibilityControllerFactory(prefsProvider);
  }

  public static LanVisibilityController provideLanVisibilityController(AppPreferences prefs) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLanVisibilityController(prefs));
  }
}
