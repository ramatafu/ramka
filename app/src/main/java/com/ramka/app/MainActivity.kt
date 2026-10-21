package com.ramka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ramka.app.panic.PanicBlackout
import com.ramka.app.panic.PanicButtonManager
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.navigation.RamkaNavHost
import com.ramka.app.ui.theme.RamkaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backgroundDeliveryController: BackgroundDeliveryController
    @Inject lateinit var panicButtonManager: PanicButtonManager

    override fun onStart() {
        super.onStart()
        // Приложение на экране — единственное безопасное место для старта foreground-сервиса
        // (из фона это запрещено на Android 12+). Если под-тумблер выключен — сервис гасится.
        backgroundDeliveryController.applyService()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamkaTheme {
                val blackout by panicButtonManager.blackout.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    RamkaNavHost()
                    // Экстренный сброс: чёрный экран с текстом поверх всего, касания поглощаются.
                    if (blackout) PanicBlackout()
                }
            }
        }
    }
}
