package com.ramka.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import org.webrtc.EglBase;

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
public final class WebRtcModule_ProvideEglBaseFactory implements Factory<EglBase> {
  @Override
  public EglBase get() {
    return provideEglBase();
  }

  public static WebRtcModule_ProvideEglBaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static EglBase provideEglBase() {
    return Preconditions.checkNotNullFromProvides(WebRtcModule.INSTANCE.provideEglBase());
  }

  private static final class InstanceHolder {
    private static final WebRtcModule_ProvideEglBaseFactory INSTANCE = new WebRtcModule_ProvideEglBaseFactory();
  }
}
