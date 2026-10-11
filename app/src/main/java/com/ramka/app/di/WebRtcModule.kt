package com.ramka.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Singleton

/**
 * WebRTC (звонки). Предоставляет единственные на приложение [EglBase] и [PeerConnectionFactory]; фабрика тяжёлая
 * (грузит нативную библиотеку), поэтому потребители берут её через `dagger.Lazy` — инициализация
 * происходит при первом звонке, а не при старте приложения.
 *
 * Видео (шаг 7.4): корневой [EglBase] общий для кодеков фабрики, захвата камеры и всех SurfaceViewRenderer —
 * кадры остаются в GPU-текстурах. Живёт столько же, сколько процесс: фабрика держит его контекст внутри
 * кодеков, поэтому закрывать его после отдельного звонка нельзя (следующий звонок получил бы мёртвый контекст).
 *
 * ICE-серверы (STUN всегда, TURN из настроек пользователя — TurnSettingsDataStore) собираются в CallViewModel
 * при каждом звонке и передаются в RTCConfiguration.
 */
@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    /** Корневой EGL-контекст; создаётся лениво — при первой выдаче (первый звонок или первый экран звонка). */
    @Provides
    @Singleton
    fun provideEglBase(): EglBase = EglBase.create()

    @Provides
    @Singleton
    fun providePeerConnectionFactory(
        @ApplicationContext context: Context,
        eglBase: EglBase,
    ): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        // Без фабрик кодеков PeerConnection не сможет ни отправить, ни принять видео (в SDP не будет видеокодеков).
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
}
