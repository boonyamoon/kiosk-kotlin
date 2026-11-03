package com.pos.kiosk.core.session

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** MainActivity reports every touch here; the order flow watches it to restart abandoned sessions. */
@Singleton
class IdleTimeoutController @Inject constructor() {
    private val _lastInteraction = MutableStateFlow(SystemClock.elapsedRealtime())
    val lastInteraction: StateFlow<Long> = _lastInteraction.asStateFlow()

    fun onInteraction() {
        _lastInteraction.value = SystemClock.elapsedRealtime()
    }
}
