package com.ramka.app.ui.qr;

import com.ramka.crypto.keys.KeyManager;
import com.ramka.domain.repository.ContactRepository;
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
public final class QrViewModel_Factory implements Factory<QrViewModel> {
  private final Provider<KeyManager> keyManagerProvider;

  private final Provider<ContactRepository> contactRepositoryProvider;

  public QrViewModel_Factory(Provider<KeyManager> keyManagerProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    this.keyManagerProvider = keyManagerProvider;
    this.contactRepositoryProvider = contactRepositoryProvider;
  }

  @Override
  public QrViewModel get() {
    return newInstance(keyManagerProvider.get(), contactRepositoryProvider.get());
  }

  public static QrViewModel_Factory create(Provider<KeyManager> keyManagerProvider,
      Provider<ContactRepository> contactRepositoryProvider) {
    return new QrViewModel_Factory(keyManagerProvider, contactRepositoryProvider);
  }

  public static QrViewModel newInstance(KeyManager keyManager,
      ContactRepository contactRepository) {
    return new QrViewModel(keyManager, contactRepository);
  }
}
