package com.ramka.app.di;

import android.content.Context;
import com.ramka.app.relay.LocalNetworkDetector;
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
public final class AppModule_ProvideLocalNetworkDetectorFactory implements Factory<LocalNetworkDetector> {
  private final Provider<Context> ctxProvider;

  public AppModule_ProvideLocalNetworkDetectorFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public LocalNetworkDetector get() {
    return provideLocalNetworkDetector(ctxProvider.get());
  }

  public static AppModule_ProvideLocalNetworkDetectorFactory create(Provider<Context> ctxProvider) {
    return new AppModule_ProvideLocalNetworkDetectorFactory(ctxProvider);
  }

  public static LocalNetworkDetector provideLocalNetworkDetector(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLocalNetworkDetector(ctx));
  }
}
