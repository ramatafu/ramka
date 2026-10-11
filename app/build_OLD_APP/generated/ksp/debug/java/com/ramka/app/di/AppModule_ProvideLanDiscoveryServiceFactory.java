package com.ramka.app.di;

import android.content.Context;
import com.ramka.network.local.LanDiscoveryService;
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
public final class AppModule_ProvideLanDiscoveryServiceFactory implements Factory<LanDiscoveryService> {
  private final Provider<Context> ctxProvider;

  public AppModule_ProvideLanDiscoveryServiceFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public LanDiscoveryService get() {
    return provideLanDiscoveryService(ctxProvider.get());
  }

  public static AppModule_ProvideLanDiscoveryServiceFactory create(Provider<Context> ctxProvider) {
    return new AppModule_ProvideLanDiscoveryServiceFactory(ctxProvider);
  }

  public static LanDiscoveryService provideLanDiscoveryService(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLanDiscoveryService(ctx));
  }
}
