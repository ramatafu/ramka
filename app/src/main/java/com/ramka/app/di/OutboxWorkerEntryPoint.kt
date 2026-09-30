package com.ramka.app.di

import com.ramka.domain.usecase.ProcessOutboxUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Точка входа для получения [ProcessOutboxUseCase] из мест, которые Hilt не
 * создаёт сам (обычный `CoroutineWorker`, не `@HiltWorker`). Так дешевле по
 * зависимостям, чем подключать отдельный `androidx.hilt:hilt-work` ради одного
 * воркера — обошлись базовым механизмом Hilt (`@EntryPoint`), который и так уже
 * доступен вместе с `hilt-android`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OutboxWorkerEntryPoint {
    fun processOutboxUseCase(): ProcessOutboxUseCase
}
