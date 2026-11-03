package com.pos.kiosk.core.session

import com.pos.kiosk.domain.repository.MenuRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps sold-out items current while a customer is ordering: asks the backend which products can't be ordered
 * right now, at once and then every [intervalMs]. The full menu (names, prices, photos) still reloads on its own,
 * slower schedule; this call returns ids only, so it is cheap enough to repeat.
 *
 * Runs only while the caller's coroutine does (OrderNavHost: kiosk on screen and on an ordering screen); a failed
 * poll (offline) is skipped and the last known set stays.
 */
@Singleton
class AvailabilityPoller @Inject constructor(private val menu: MenuRepository) {

    suspend fun run(intervalMs: Long = INTERVAL_MS) {
        while (true) {
            menu.refreshAvailability()
            delay(intervalMs)
        }
    }

    /** A one-off check, e.g. right after the server refused an item at checkout. */
    suspend fun pollNow() {
        menu.refreshAvailability()
    }

    companion object {
        const val INTERVAL_MS = 20_000L
    }
}
