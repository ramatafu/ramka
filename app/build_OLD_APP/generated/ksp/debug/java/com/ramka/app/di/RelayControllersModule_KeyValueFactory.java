package com.ramka.app.di;

import android.content.SharedPreferences;
import com.ramka.app.relay.RelayKeyValue;
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
public final class RelayControllersModule_KeyValueFactory implements Factory<RelayKeyValue> {
  private final Provider<SharedPreferences> prefsProvider;

  public RelayControllersModule_KeyValueFactory(Provider<SharedPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public RelayKeyValue get() {
    return keyValue(prefsProvider.get());
  }

  public static RelayControllersModule_KeyValueFactory create(
      Provider<SharedPreferences> prefsProvider) {
    return new RelayControllersModule_KeyValueFactory(prefsProvider);
  }

  public static RelayKeyValue keyValue(SharedPreferences prefs) {
    return Preconditions.checkNotNullFromProvides(RelayControllersModule.INSTANCE.keyValue(prefs));
  }
}
