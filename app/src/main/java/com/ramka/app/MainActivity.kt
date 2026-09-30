package com.ramka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.navigation.RamkaNavHost
import com.ramka.app.ui.theme.RamkaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backgroundDeliveryController: BackgroundDeliveryController

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
                RamkaNavHost()
            }
        }
    }
}
