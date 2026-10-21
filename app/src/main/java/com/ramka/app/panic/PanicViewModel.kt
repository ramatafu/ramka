package com.ramka.app.panic

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Тонкая обёртка: чтобы экраны получали [PanicButtonManager] через hiltViewModel(). Состояния не хранит. */
@HiltViewModel
class PanicViewModel @Inject constructor(
    private val manager: PanicButtonManager
) : ViewModel() {
    fun panic(activity: Activity?) = manager.executePanic(activity)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
