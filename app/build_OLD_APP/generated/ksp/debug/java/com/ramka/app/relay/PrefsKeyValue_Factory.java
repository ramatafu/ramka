package com.ramka.app.relay;

import android.content.SharedPreferences;
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
public final class PrefsKeyValue_Factory implements Factory<PrefsKeyValue> {
  private final Provider<SharedPreferences> prefsProvider;

  public PrefsKeyValue_Factory(Provider<SharedPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public PrefsKeyValue get() {
    return newInstance(prefsProvider.get());
  }

  public static PrefsKeyValue_Factory create(Provider<SharedPreferences> prefsProvider) {
    return new PrefsKeyValue_Factory(prefsProvider);
  }

  public static PrefsKeyValue newInstance(SharedPreferences prefs) {
    return new PrefsKeyValue(prefs);
  }
}
