package com.pos.kiosk.domain.usecase

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.Clock
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // one instance app-wide, so its lock really is single-flight
class RefreshKioskTokenUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val clock: Clock,
) {
    private val refreshLock = Mutex()

    /**
     * Refreshes once less than half of the token's lifetime is left (or when forced). Single-flight:
     * a second caller waits for the first and then sees the fresh token, instead of refreshing again.
     */
    suspend operator fun invoke(force: Boolean = false): AppResult<Unit> = refreshLock.withLock {
        val lifetime = auth.tokenLifetime() ?: return@withLock AppResult.Failure(AppError.TerminalRevoked)
        val total = lifetime.expiresAtMillis - lifetime.issuedAtMillis
        val remaining = lifetime.expiresAtMillis - clock.nowMillis()
        if (!force && total > 0 && remaining > total / 2) return@withLock AppResult.Success(Unit)
        auth.refreshToken()
    }
}

sealed interface BootstrapResult {
    data object NeedsSetup : BootstrapResult
    data object Ready : BootstrapResult
    data object OutOfService : BootstrapResult
    data object NotEnabled : BootstrapResult
    data class Failed(val error: AppError) : BootstrapResult
}

class BootstrapKioskUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val refreshToken: RefreshKioskTokenUseCase,
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(): BootstrapResult {
        auth.pairedTerminal() ?: return BootstrapResult.NeedsSetup

        // A network failure here is fine — the current token may still be valid; config decides.
        (refreshToken() as? AppResult.Failure)?.let { failure -> terminalState(failure.error)?.let { return it } }

        return when (val config = configRepository.refresh()) {
            is AppResult.Success -> BootstrapResult.Ready
            is AppResult.Failure -> terminalState(config.error) ?: BootstrapResult.Failed(config.error)
        }
    }

    private suspend fun terminalState(error: AppError): BootstrapResult? = when (error) {
        AppError.TerminalRevoked -> {
            auth.forgetPairing()
            BootstrapResult.NeedsSetup
        }
        AppError.TerminalDisabled -> BootstrapResult.OutOfService
        AppError.KioskNotEnabled -> BootstrapResult.NotEnabled
        else -> null
    }
}
