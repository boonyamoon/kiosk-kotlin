package com.pos.kiosk.feature.setup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.KioskBranch
import com.pos.kiosk.domain.model.KioskTerminalOption
import com.pos.kiosk.domain.model.StaffSession
import com.pos.kiosk.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep {
    LOGIN,
    BRANCH,
    TERMINAL,
    /** Signed in, but the account has no store at all. */
    NO_BUSINESS,
    /** Signed in and has a store, but allow_kiosk_login is off for it. */
    NO_KIOSK_PERMISSION,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.LOGIN,
    val email: String = "",
    val password: String = "",
    val branches: List<KioskBranch> = emptyList(),
    val selectedBranch: KioskBranch? = null,
    val terminals: List<KioskTerminalOption> = emptyList(),
    /** null = let the server pick any free kiosk terminal. */
    val selectedTerminalId: String? = null,
    val deviceName: String = Build.MODEL.orEmpty(),
    val loading: Boolean = false,
    val error: AppError? = null,
    /** Sign-in was pressed with an empty/invalid email or empty password. */
    val missingCredentials: Boolean = false,
) {
    val canLogin: Boolean get() = email.contains('@') && password.isNotEmpty() && !loading
    val canPair: Boolean get() = selectedBranch != null && deviceName.isNotBlank() && !loading
}

sealed interface SetupEvent {
    data class EmailChanged(val value: String) : SetupEvent
    data class PasswordChanged(val value: String) : SetupEvent
    data object Login : SetupEvent
    data class SelectBranch(val branch: KioskBranch) : SetupEvent
    data object ConfirmBranch : SetupEvent
    data class SelectTerminal(val terminalId: String?) : SetupEvent
    data class DeviceNameChanged(val value: String) : SetupEvent
    data object Pair : SetupEvent
    data object Back : SetupEvent
    data object SignOut : SetupEvent
}

sealed interface SetupEffect {
    data object Paired : SetupEffect
}

/**
 * The staff token lives only in this ViewModel — it is never persisted and is dropped as soon as
 * pairing succeeds (only the kiosk token is stored).
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private val _effects = Channel<SetupEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var staff: StaffSession? = null

    fun onEvent(event: SetupEvent) {
        when (event) {
            is SetupEvent.EmailChanged -> _state.update { it.copy(email = event.value.trim(), error = null, missingCredentials = false) }
            is SetupEvent.PasswordChanged -> _state.update { it.copy(password = event.value, error = null, missingCredentials = false) }
            SetupEvent.Login -> login()
            is SetupEvent.SelectBranch -> _state.update { if (it.loading) it else it.copy(selectedBranch = event.branch, error = null) }
            SetupEvent.ConfirmBranch -> confirmBranch()
            is SetupEvent.SelectTerminal -> _state.update { it.copy(selectedTerminalId = event.terminalId) }
            is SetupEvent.DeviceNameChanged -> _state.update { it.copy(deviceName = event.value) }
            SetupEvent.Pair -> pair()
            SetupEvent.Back -> back()
            SetupEvent.SignOut -> signOut()
        }
    }

    private fun login() {
        if (!_state.value.canLogin) {
            if (!_state.value.loading) _state.update { it.copy(missingCredentials = true, error = null) }
            return
        }
        authenticate()
    }

    private fun authenticate() = launchLoading {
        val current = _state.value
        when (val login = auth.staffLogin(current.email, current.password)) {
            is AppResult.Failure -> _state.update { it.copy(error = login.error) }
            is AppResult.Success -> {
                staff = login.data
                when (val access = auth.kioskAccess(login.data)) {
                    is AppResult.Failure -> _state.update { it.copy(error = access.error) }
                    is AppResult.Success -> {
                        val branches = access.data.branches
                        val step = when {
                            branches.isNotEmpty() -> SetupStep.BRANCH
                            access.data.deniedByPermission -> SetupStep.NO_KIOSK_PERMISSION
                            else -> SetupStep.NO_BUSINESS
                        }
                        // Nothing more to do with this account — drop its token right away.
                        if (step != SetupStep.BRANCH) staff = null
                        _state.update {
                            it.copy(step = step, branches = branches, selectedBranch = branches.singleOrNull(), password = "", error = null)
                        }
                    }
                }
            }
        }
    }

    private fun confirmBranch() = launchLoading {
        val branch = _state.value.selectedBranch ?: return@launchLoading
        val staffSession = staff ?: return@launchLoading backToLogin()
        when (val terminals = auth.kioskTerminals(staffSession, branch.id)) {
            is AppResult.Failure -> _state.update { it.copy(error = terminals.error) }
            is AppResult.Success -> _state.update {
                it.copy(step = SetupStep.TERMINAL, terminals = terminals.data, selectedTerminalId = null)
            }
        }
    }

    private fun pair() = launchLoading {
        val current = _state.value
        val staffSession = staff ?: return@launchLoading backToLogin()
        val branch = current.selectedBranch ?: return@launchLoading
        when (val result = auth.pair(staffSession, branch.id, current.selectedTerminalId, current.deviceName)) {
            is AppResult.Failure -> _state.update { it.copy(error = result.error) }
            is AppResult.Success -> {
                staff = null
                _effects.send(SetupEffect.Paired)
            }
        }
    }

    private fun back() {
        when (_state.value.step) {
            SetupStep.LOGIN -> Unit
            SetupStep.TERMINAL -> _state.update { it.copy(step = SetupStep.BRANCH, error = null) }
            SetupStep.BRANCH, SetupStep.NO_BUSINESS, SetupStep.NO_KIOSK_PERMISSION -> signOut()
        }
    }

    /** Back to the login form; the email stays filled so staff can retry once access is granted. */
    private fun signOut() {
        staff = null
        backToLogin()
    }

    private fun backToLogin() = _state.update { SetupUiState(email = it.email) }

    private fun launchLoading(block: suspend () -> Unit) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                block()
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    override fun onCleared() {
        staff = null
    }
}
