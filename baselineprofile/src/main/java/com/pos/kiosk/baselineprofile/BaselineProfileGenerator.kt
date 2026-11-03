package com.pos.kiosk.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the kiosk uses, so they are compiled ahead of time on install
 * instead of being interpreted/JIT-compiled the first time a customer scrolls the menu. Run with
 * `./gradlew :app:generateBaselineProfile` (see README.md); the output lands in
 * `app/src/release/generated/baselineProfiles/`.
 *
 * No pressHome() anywhere: the kiosk is itself a HOME app, so Home could open a launcher chooser.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Launch → attract screen. Also the startup profile, which decides which classes go first in the dex. */
    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        startActivityAndWait()
        pairIfNeeded()
        waitForAttract()
    }

    /** The customer journey after startup: baseline profile only, so it doesn't dilute the startup dex layout. */
    @Test
    fun orderJourney() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
        startActivityAndWait()
        pairIfNeeded()
        orderJourney()
    }
}
