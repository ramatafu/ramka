package com.ramka.app.di

import android.content.Context
import com.ramka.app.datastore.ProxyDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object ProxyModule {

    @Provides
    @ViewModelScoped
    fun provideProxyDataStore(
        @ApplicationContext context: Context
    ): ProxyDataStore {
        return ProxyDataStore(context)
    }
}