package com.pos.kiosk.core.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Wall-clock time, injectable so time-based logic (token refresh, QR expiry) is testable. */
fun interface Clock {
    fun nowMillis(): Long
}

class WallClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds abstract fun clock(impl: WallClock): Clock
}
