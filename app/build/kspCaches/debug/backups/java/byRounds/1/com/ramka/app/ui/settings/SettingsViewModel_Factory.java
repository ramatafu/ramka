package com.ramka.app.ui.settings;

import com.ramka.app.background.BackgroundDeliveryController;
import com.ramka.app.discovery.LanVisibilityController;
import com.ramka.app.preferences.AppPreferences;
import com.ramka.app.relay.RelayFormController;
import com.ramka.domain.relay.SingleRelayProvider;
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

  private final Provider<RelayFormController> relayControllerProvider;

  private final Provider<SingleRelayProvider> relayProvider;

  public SettingsViewModel_Factory(Provider<BackgroundDeliveryController> controllerProvider,
      Provider<LanVisibilityController> lanVisibilityProvider,
      Provider<AppPreferences> prefsProvider, Provider<RelayFormController> relayControllerProvider,
      Provider<SingleRelayProvider> relayProvider) {
    this.controllerProvider = controllerProvider;
    this.lanVisibilityProvider = lanVisibilityProvider;
    this.prefsProvider = prefsProvider;
    this.relayControllerProvider = relayControllerProvider;
    this.relayProvider = relayProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(controllerProvider.get(), lanVisibilityProvider.get(), prefsProvider.get(), relayControllerProvider.get(), relayProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<BackgroundDeliveryController> controllerProvider,
      Provider<LanVisibilityController> lanVisibilityProvider,
      Provider<AppPreferences> prefsProvider, Provider<RelayFormController> relayControllerProvider,
      Provider<SingleRelayProvider> relayProvider) {
    return new SettingsViewModel_Factory(controllerProvider, lanVisibilityProvider, prefsProvider, relayControllerProvider, relayProvider);
  }

  public static SettingsViewModel newInstance(BackgroundDeliveryController controller,
      LanVisibilityController lanVisibility, AppPreferences prefs,
      RelayFormController relayController, SingleRelayProvider relayProvider) {
    return new SettingsViewModel(controller, lanVisibility, prefs, relayController, relayProvider);
  }
}
