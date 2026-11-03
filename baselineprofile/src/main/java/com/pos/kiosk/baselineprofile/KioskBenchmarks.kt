package com.pos.kiosk.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures what the baseline profile buys: cold start, and frame timing on first use of the menu, each
 * without any ahead-of-time compilation vs. with the profile. A fresh install is paired in the setup block
 * (same `kioskEmail` / `kioskPassword` arguments as the generator), outside the measured part.
 * Run on a physical tablet for numbers worth comparing; emulators are only indicative.
 */
@RunWith(AndroidJUnit4::class)
class KioskBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun menuWithoutProfile() = menu(CompilationMode.None())

    @Test
    fun menuWithProfile() = menu(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            // Not measured: make sure the kiosk is paired, so the measured launch lands on the attract screen.
            startActivityAndWait()
            pairIfNeeded()
        },
    ) {
        startActivityAndWait()
        waitForAttract()
    }

    /**
     * First use of the menu after a fresh process start (no JIT warm-up yet), which is what the first customer
     * sees: scroll the grid, open an option sheet, scroll it, close it. startupMode stays null: COLD would kill
     * the app between setupBlock and the measured part, so the process is restarted by hand in setupBlock.
     */
    private fun menu(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = null,
        iterations = ITERATIONS,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            pairIfNeeded()
            openMenu()
        },
    ) {
        useMenu()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
