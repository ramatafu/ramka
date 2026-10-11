package com.ramka.app.di;

import android.content.Context;
import com.ramka.crypto.securestorage.SecureKeyStore;
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
public final class AppModule_ProvideSecureKeyStoreFactory implements Factory<SecureKeyStore> {
  private final Provider<Context> ctxProvider;

  public AppModule_ProvideSecureKeyStoreFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public SecureKeyStore get() {
    return provideSecureKeyStore(ctxProvider.get());
  }

  public static AppModule_ProvideSecureKeyStoreFactory create(Provider<Context> ctxProvider) {
    return new AppModule_ProvideSecureKeyStoreFactory(ctxProvider);
  }

  public static SecureKeyStore provideSecureKeyStore(Context ctx) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSecureKeyStore(ctx));
  }
}
