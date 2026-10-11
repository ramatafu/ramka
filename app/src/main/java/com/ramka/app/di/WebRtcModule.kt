package com.ramka.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Singleton

/**
 * WebRTC (звонки). Предоставляет единственный на приложение [PeerConnectionFactory]; фабрика тяжёлая
 * (грузит нативную библиотеку), поэтому потребители берут её через `dagger.Lazy` — инициализация
 * происходит при первом звонке, а не при старте приложения.
 *
 * Пока только аудио (шаг 7.3): видеокодеки и EglBase добавятся в 7.4 в этом же модуле.
 * STUN/TURN не настраиваются — звонки работают только внутри одной локальной сети.
 */
@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    @Provides
    @Singleton
    fun providePeerConnectionFactory(@ApplicationContext context: Context): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }
}
