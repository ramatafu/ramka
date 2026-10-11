package com.ramka.app.ui.contacts;

import com.ramka.app.preferences.AppPreferences;
import com.ramka.domain.repository.ContactRepository;
import com.ramka.domain.usecase.ProcessOutboxUseCase;
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
public final class ContactsViewModel_Factory implements Factory<ContactsViewModel> {
  private final Provider<ContactRepository> contactRepositoryProvider;

  private final Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider;

  private final Provider<AppPreferences> appPreferencesProvider;

  public ContactsViewModel_Factory(Provider<ContactRepository> contactRepositoryProvider,
      Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider,
      Provider<AppPreferences> appPreferencesProvider) {
    this.contactRepositoryProvider = contactRepositoryProvider;
    this.processOutboxUseCaseProvider = processOutboxUseCaseProvider;
    this.appPreferencesProvider = appPreferencesProvider;
  }

  @Override
  public ContactsViewModel get() {
    return newInstance(contactRepositoryProvider.get(), processOutboxUseCaseProvider.get(), appPreferencesProvider.get());
  }

  public static ContactsViewModel_Factory create(
      Provider<ContactRepository> contactRepositoryProvider,
      Provider<ProcessOutboxUseCase> processOutboxUseCaseProvider,
      Provider<AppPreferences> appPreferencesProvider) {
    return new ContactsViewModel_Factory(contactRepositoryProvider, processOutboxUseCaseProvider, appPreferencesProvider);
  }

  public static ContactsViewModel newInstance(ContactRepository contactRepository,
      ProcessOutboxUseCase processOutboxUseCase, AppPreferences appPreferences) {
    return new ContactsViewModel(contactRepository, processOutboxUseCase, appPreferences);
  }
}
