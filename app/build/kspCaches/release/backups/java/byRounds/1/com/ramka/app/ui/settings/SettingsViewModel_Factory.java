package com.ramka.app.ui.settings;

import com.ramka.app.background.BackgroundDeliveryController;
import com.ramka.app.discovery.LanVisibilityController;
import com.ramka.app.preferences.AppPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<BackgroundDeliveryController> controllerProvider;

  private final Provider<LanVisibilityController> lanVisibilityProvider;

  private final Provider<AppPreferences> prefsProvider;

  public SettingsViewModel_Factory(Provider<BackgroundDeliveryController> controllerProvider,
      Provider<LanVisibilityController> lanVisibilityProvider,
      Provider<AppPreferences> prefsProvider) {
    this.controllerProvider = controllerProvider;
    this.lanVisibilityProvider = lanVisibilityProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(controllerProvider.get(), lanVisibilityProvider.get(), prefsProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<BackgroundDeliveryController> controllerProvider,
      Provider<LanVisibilityController> lanVisibilityProvider,
      Provider<AppPreferences> prefsProvider) {
    return new SettingsViewModel_Factory(controllerProvider, lanVisibilityProvider, prefsProvider);
  }

  public static SettingsViewModel newInstance(BackgroundDeliveryController controller,
      LanVisibilityController lanVisibility, AppPreferences prefs) {
    return new SettingsViewModel(controller, lanVisibility, prefs);
  }
}
