package com.pos.kiosk.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.network.KioskSessionEvent
import com.pos.kiosk.core.network.KioskSessionEvents
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.usecase.BootstrapKioskUseCase
import com.pos.kiosk.domain.usecase.BootstrapResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object Ready : RootState
    data object OutOfService : RootState

    /** Paired, but the shop's plan doesn't include the kiosk (`enable_kiosk`). */
    data object NotEnabled : RootState
    data class Failed(val error: AppError) : RootState
}

/** Decides which top-level surface is shown: setup, the order flow, or out of service. */
@HiltViewModel
class KioskRootViewModel @Inject constructor(
    private val bootstrapKiosk: BootstrapKioskUseCase,
    private val auth: AuthRepository,
    private val session: OrderSessionRepository,
    configRepository: ConfigRepository,
    sessionEvents: KioskSessionEvents,
) : ViewModel() {

    private val _state = MutableStateFlow<RootState>(RootState.Loading)
    val state: StateFlow<RootState> = _state.asStateFlow()

    val config: StateFlow<KioskConfig?> = configRepository.config

    // Declared before init: init calls bootstrap(), which assigns it.
    private var bootstrapJob: Job? = null

    init {
        bootstrap()
        viewModelScope.launch {
            sessionEvents.events.collect { event ->
                session.reset()
                when (event) {
                    KioskSessionEvent.REVOKED -> {
                        auth.forgetPairing()
                        _state.value = RootState.NeedsSetup
                    }
                    KioskSessionEvent.DISABLED -> _state.value = RootState.OutOfService
                    KioskSessionEvent.NOT_ENABLED -> _state.value = RootState.NotEnabled
                }
            }
        }
    }

    /** A retry while a bootstrap is still running restarts it, so only the newest result can land. */
    fun bootstrap() {
        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            _state.value = RootState.Loading
            _state.value = when (val result = bootstrapKiosk()) {
                BootstrapResult.NeedsSetup -> RootState.NeedsSetup
                BootstrapResult.Ready -> RootState.Ready
                BootstrapResult.OutOfService -> RootState.OutOfService
                BootstrapResult.NotEnabled -> RootState.NotEnabled
                is BootstrapResult.Failed -> RootState.Failed(result.error)
            }
        }
    }
}
