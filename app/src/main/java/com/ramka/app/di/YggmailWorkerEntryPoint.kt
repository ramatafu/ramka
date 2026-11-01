package com.ramka.app.di

import com.ramka.app.yggmail.YggmailInbox
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Точка входа для [com.ramka.app.yggmail.YggmailPollingWorker] (обычный CoroutineWorker, как у Outbox). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface YggmailWorkerEntryPoint {
    fun yggmailInbox(): YggmailInbox
}
