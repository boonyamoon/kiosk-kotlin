package com.pos.kiosk.domain.usecase

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.PairedTerminal
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.TokenLifetime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshKioskTokenUseCaseTest {
    private val auth = mockk<AuthRepository>()
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `does nothing while more than half of the lifetime is left`() = runTest {
        coEvery { auth.tokenLifetime() } returns TokenLifetime(issuedAtMillis = 0, expiresAtMillis = 7 * day)
        val refresh = RefreshKioskTokenUseCase(auth) { 2 * day }

        assertEquals(AppResult.Success(Unit), refresh())
        coVerify(exactly = 0) { auth.refreshToken() }
    }

    @Test
    fun `refreshes once less than half is left`() = runTest {
        coEvery { auth.tokenLifetime() } returns TokenLifetime(issuedAtMillis = 0, expiresAtMillis = 7 * day)
        coEvery { auth.refreshToken() } returns AppResult.Success(Unit)
        val refresh = RefreshKioskTokenUseCase(auth) { 4 * day }

        refresh()
        coVerify(exactly = 1) { auth.refreshToken() }
    }

    @Test
    fun `no stored token means the terminal must be set up`() = runTest {
        coEvery { auth.tokenLifetime() } returns null
        assertEquals(AppResult.Failure(AppError.TerminalRevoked), RefreshKioskTokenUseCase(auth) { 0 }())
    }
}

class BootstrapKioskUseCaseTest {
    private val auth = mockk<AuthRepository>(relaxUnitFun = true)
    private val refresh = mockk<RefreshKioskTokenUseCase>()
    private val config = mockk<ConfigRepository>()
    private val bootstrap = BootstrapKioskUseCase(auth, refresh, config)
    private val terminal = PairedTerminal("t-1", "K01", "Kiosk 1", "biz-1", "branch-1")

    @Test
    fun `unpaired device goes to setup`() = runTest {
        coEvery { auth.pairedTerminal() } returns null
        assertEquals(BootstrapResult.NeedsSetup, bootstrap())
    }

    @Test
    fun `revoked terminal forgets its pairing and goes to setup`() = runTest {
        coEvery { auth.pairedTerminal() } returns terminal
        coEvery { refresh.invoke(any()) } returns AppResult.Failure(AppError.TerminalRevoked)

        assertEquals(BootstrapResult.NeedsSetup, bootstrap())
        coVerify { auth.forgetPairing() }
    }

    @Test
    fun `disabled terminal is out of service`() = runTest {
        coEvery { auth.pairedTerminal() } returns terminal
        coEvery { refresh.invoke(any()) } returns AppResult.Success(Unit)
        coEvery { config.refresh() } returns AppResult.Failure(AppError.TerminalDisabled)

        assertEquals(BootstrapResult.OutOfService, bootstrap())
    }

    @Test
    fun `a network error on refresh still tries to load config`() = runTest {
        coEvery { auth.pairedTerminal() } returns terminal
        coEvery { refresh.invoke(any()) } returns AppResult.Failure(AppError.Network)
        coEvery { config.refresh() } returns AppResult.Failure(AppError.Network)

        assertEquals(BootstrapResult.Failed(AppError.Network), bootstrap())
        coVerify { config.refresh() }
    }
}
