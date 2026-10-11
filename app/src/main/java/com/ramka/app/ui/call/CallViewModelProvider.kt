package com.ramka.app.ui.call

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Единственный на Activity [CallViewModel]. Его берут и RamkaNavHost (ждёт входящих звонков и открывает экран),
 * и CallScreen — оба получают один и тот же экземпляр, потому что владелец — Activity, а не запись навигации.
 */
@Composable
fun activityCallViewModel(): CallViewModel =
    hiltViewModel(viewModelStoreOwner = LocalContext.current.findComponentActivity())

private tailrec fun Context.findComponentActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> throw IllegalStateException("Контекст экрана звонка не принадлежит ComponentActivity")
}
