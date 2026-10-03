package com.ramka.app;

import com.ramka.app.background.BackgroundDeliveryController;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider;

  public MainActivity_MembersInjector(
      Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider) {
    this.backgroundDeliveryControllerProvider = backgroundDeliveryControllerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<BackgroundDeliveryController> backgroundDeliveryControllerProvider) {
    return new MainActivity_MembersInjector(backgroundDeliveryControllerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectBackgroundDeliveryController(instance, backgroundDeliveryControllerProvider.get());
  }

  @InjectedFieldSignature("com.ramka.app.MainActivity.backgroundDeliveryController")
  public static void injectBackgroundDeliveryController(MainActivity instance,
      BackgroundDeliveryController backgroundDeliveryController) {
    instance.backgroundDeliveryController = backgroundDeliveryController;
  }
}
