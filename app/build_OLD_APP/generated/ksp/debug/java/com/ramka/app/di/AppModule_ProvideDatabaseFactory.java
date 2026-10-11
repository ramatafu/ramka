package com.ramka.app.di;

import android.content.Context;
import com.ramka.crypto.securestorage.SecureKeyStore;
import com.ramka.storage.db.RamkaDatabase;
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
public final class AppModule_ProvideDatabaseFactory implements Factory<RamkaDatabase> {
  private final Provider<Context> ctxProvider;

  private final Provider<SecureKeyStore> secureKeyStoreProvider;

  public AppModule_ProvideDatabaseFactory(Provider<Context> ctxProvider,
      Provider<SecureKeyStore> secureKeyStoreProvider) {
    this.ctxProvider = ctxProvider;
    this.secureKeyStoreProvider = secureKeyStoreProvider;
  }

  @Override
  public RamkaDatabase get() {
    return provideDatabase(ctxProvider.get(), secureKeyStoreProvider.get());
  }

  public static AppModule_ProvideDatabaseFactory create(Provider<Context> ctxProvider,
      Provider<SecureKeyStore> secureKeyStoreProvider) {
    return new AppModule_ProvideDatabaseFactory(ctxProvider, secureKeyStoreProvider);
  }

  public static RamkaDatabase provideDatabase(Context ctx, SecureKeyStore secureKeyStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDatabase(ctx, secureKeyStore));
  }
}
