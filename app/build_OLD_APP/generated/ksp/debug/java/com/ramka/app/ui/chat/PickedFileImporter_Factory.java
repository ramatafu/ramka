package com.ramka.app.ui.chat;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class PickedFileImporter_Factory implements Factory<PickedFileImporter> {
  private final Provider<Context> contextProvider;

  public PickedFileImporter_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PickedFileImporter get() {
    return newInstance(contextProvider.get());
  }

  public static PickedFileImporter_Factory create(Provider<Context> contextProvider) {
    return new PickedFileImporter_Factory(contextProvider);
  }

  public static PickedFileImporter newInstance(Context context) {
    return new PickedFileImporter(context);
  }
}
