package com.pos.kiosk.core.session

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.repository.MenuRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityPollerTest {
    private val menu = mockk<MenuRepository>()

    @Test
    fun `polls at once, then every interval, and stops with its scope`() = runTest {
        coEvery { menu.refreshAvailability() } returns AppResult.Success(emptySet())
        val job = launch { AvailabilityPoller(menu).run(intervalMs = 20_000) }

        runCurrent()
        coVerify(exactly = 1) { menu.refreshAvailability() }
        advanceTimeBy(40_001)
        coVerify(exactly = 3) { menu.refreshAvailability() }

        job.cancel()
        advanceTimeBy(60_000)
        coVerify(exactly = 3) { menu.refreshAvailability() }
    }

    @Test
    fun `an offline poll is skipped and polling carries on`() = runTest {
        coEvery { menu.refreshAvailability() } returnsMany listOf(AppResult.Failure(AppError.Network), AppResult.Success(setOf("p1")))
        val job = launch { AvailabilityPoller(menu).run(intervalMs = 20_000) }

        advanceTimeBy(20_001)
        coVerify(exactly = 2) { menu.refreshAvailability() }
        job.cancel()
    }
}
