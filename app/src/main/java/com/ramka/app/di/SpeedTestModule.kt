package com.ramka.app.di

import com.ramka.app.speedtest.HttpUrlSpeedTestTransfer
import com.ramka.app.speedtest.SpeedTestTransfer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeedTestModule {
    @Binds
    abstract fun bindSpeedTestTransfer(impl: HttpUrlSpeedTestTransfer): SpeedTestTransfer
}
