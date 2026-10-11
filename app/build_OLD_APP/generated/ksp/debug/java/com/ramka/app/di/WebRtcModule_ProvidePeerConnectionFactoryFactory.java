package com.ramka.app.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;

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
public final class WebRtcModule_ProvidePeerConnectionFactoryFactory implements Factory<PeerConnectionFactory> {
  private final Provider<Context> contextProvider;

  private final Provider<EglBase> eglBaseProvider;

  public WebRtcModule_ProvidePeerConnectionFactoryFactory(Provider<Context> contextProvider,
      Provider<EglBase> eglBaseProvider) {
    this.contextProvider = contextProvider;
    this.eglBaseProvider = eglBaseProvider;
  }

  @Override
  public PeerConnectionFactory get() {
    return providePeerConnectionFactory(contextProvider.get(), eglBaseProvider.get());
  }

  public static WebRtcModule_ProvidePeerConnectionFactoryFactory create(
      Provider<Context> contextProvider, Provider<EglBase> eglBaseProvider) {
    return new WebRtcModule_ProvidePeerConnectionFactoryFactory(contextProvider, eglBaseProvider);
  }

  public static PeerConnectionFactory providePeerConnectionFactory(Context context,
      EglBase eglBase) {
    return Preconditions.checkNotNullFromProvides(WebRtcModule.INSTANCE.providePeerConnectionFactory(context, eglBase));
  }
}
