package com.ramka.app.ui.call;

import android.content.Context;
import com.ramka.domain.repository.ContactRepository;
import com.ramka.network.call.CallSignaling;
import dagger.Lazy;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;

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
public final class CallViewModel_Factory implements Factory<CallViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<PeerConnectionFactory> factoryProvider;

  private final Provider<EglBase> eglBaseProvider;

  private final Provider<CallSignaling> signalingProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  public CallViewModel_Factory(Provider<Context> contextProvider,
      Provider<PeerConnectionFactory> factoryProvider, Provider<EglBase> eglBaseProvider,
      Provider<CallSignaling> signalingProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.factoryProvider = factoryProvider;
    this.eglBaseProvider = eglBaseProvider;
    this.signalingProvider = signalingProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
  }

  @Override
  public CallViewModel get() {
    return newInstance(contextProvider.get(), DoubleCheck.lazy(factoryProvider), eglBaseProvider.get(), signalingProvider.get(), contactRepositoryProvider.get());
  }

  public static CallViewModel_Factory create(Provider<Context> contextProvider,
      Provider<PeerConnectionFactory> factoryProvider, Provider<EglBase> eglBaseProvider,
      Provider<CallSignaling> signalingProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    return new CallViewModel_Factory(contextProvider, factoryProvider, eglBaseProvider, signalingProvider, contactRepositoryProvider);
  }

  public static CallViewModel newInstance(Context context,
      Lazy<PeerConnectionFactory> factoryProvider, EglBase eglBase, CallSignaling signaling,
      ContactRepository contactRepository) {
    return new CallViewModel(context, factoryProvider, eglBase, signaling, contactRepository);
  }
}
