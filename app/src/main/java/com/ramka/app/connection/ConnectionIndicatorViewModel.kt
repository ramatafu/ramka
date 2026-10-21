package com.ramka.app.connection

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Отдаёт экрану «сырое» состояние связи; итоговый [ConnectionState] считает [resolveConnectionState]. */
@HiltViewModel
class ConnectionIndicatorViewModel @Inject constructor(
    monitor: ConnectionLinkMonitor
) : ViewModel() {
    val link: StateFlow<LinkSnapshot> = monitor.link
}
