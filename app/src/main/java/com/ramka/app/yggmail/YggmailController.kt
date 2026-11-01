package com.ramka.app.yggmail

import android.content.Context
import com.ramka.app.preferences.AppPreferences
import com.ramka.domain.util.OfflinePacketSealer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка включения Yggmail: «готов» только когда включён тумблер, настройки валидны и есть схема
 * шифрования офлайн-пакетов. От этого зависит и опрос в фоне. Ничего не бросает наружу.
 */
@Singleton
class YggmailController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val configStore: YggmailConfigStore,
    private val sealer: OfflinePacketSealer
) {
    val sealerAvailable: Boolean get() = sealer.isAvailable

    fun isReady(): Boolean = try {
        prefs.yggmailEnabled && sealer.isAvailable && configStore.load()?.isValid() == true
    } catch (e: Exception) {
        false
    }

    /** Запланировать или отменить фоновый опрос по текущему состоянию. Вызывать при старте и при смене настроек. */
    fun refreshSchedule() {
        try {
            if (isReady()) YggmailScheduler.schedule(context) else YggmailScheduler.cancel(context)
        } catch (e: Exception) {
            // WorkManager недоступен — функция просто не работает в фоне
        } catch (e: LinkageError) {
        }
    }
}
