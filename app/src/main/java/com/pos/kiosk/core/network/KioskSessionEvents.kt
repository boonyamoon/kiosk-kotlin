package com.pos.kiosk.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class KioskSessionEvent {
    REVOKED,
    DISABLED,

    /** The shop's plan no longer includes the kiosk: stay paired, show out of service until it is back. */
    NOT_ENABLED,
}

/** App-wide signal from the network layer: the kiosk must be re-paired or shown as out of service. */
@Singleton
class KioskSessionEvents @Inject constructor() {
    private val _events = MutableSharedFlow<KioskSessionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<KioskSessionEvent> = _events

    fun emit(event: KioskSessionEvent) {
        _events.tryEmit(event)
    }
}
