package com.pos.kiosk.feature.admin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.R
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.printer.PrinterInfo
import com.pos.kiosk.core.session.LockMode
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.core.session.LockTaskState
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.usecase.PrintReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Staff PINs are 4–10 digits (same as the iPad POS); pos-services checks the same range. */
const val ADMIN_PIN_MIN_LENGTH = 4
const val ADMIN_PIN_MAX_LENGTH = 10

data class AdminUiState(
    val authenticated: Boolean = false,
    val pin: String = "",
    /** Who unlocked the menu, shown once it is open. */
    val staffName: String? = null,
    val config: KioskConfig? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
    val messageRes: Int? = null,
    /** The printer receipts go to: one assigned to this kiosk, or the device's own. Null when there is none. */
    val printer: PrinterInfo? = config?.receiptPrinter?.let { PrinterInfo.Lan(it) },
    /** Whether the app is pinned to the screen, shown with the buttons that apply to it. */
    val lock: LockTaskState = LockTaskState(),
) {
    val canSubmitPin: Boolean get() = pin.length >= ADMIN_PIN_MIN_LENGTH && !busy

    /** Android opens its USB prompt as a task of its own, which a pinned or locked app refuses: nothing would appear. */
    val canAskForUsbPermission: Boolean get() = lock.mode == LockMode.Suspended || lock.mode == LockMode.NotPinned
}

sealed interface AdminEvent {
    data class PinDigit(val digit: Char) : AdminEvent
    data object PinBackspace : AdminEvent
    data object SubmitPin : AdminEvent
    data object TestPrint : AdminEvent
    data object Refresh : AdminEvent
    data object Unpair : AdminEvent
    data object AllowUsbPrinter : AdminEvent
    data class UsbPaperChosen(val widthDots: Int) : AdminEvent

    /** Back from Android's USB prompt, or from anything else that may have changed the printer. */
    data object Resumed : AdminEvent
}

sealed interface AdminEffect {
    data object Unpaired : AdminEffect
}

/**
 * Hidden staff menu (5 taps on the logo), opened with a staff PIN. pos-services decides who may open it
 * (staff of this branch allowed to use the kiosk) and locks the PIN pad after repeated wrong PINs.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val configRepository: ConfigRepository,
    private val menuRepository: MenuRepository,
    private val printReceipt: PrintReceiptUseCase,
    private val lockTask: LockTaskController,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState(config = configRepository.config.value))
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val _effects = Channel<AdminEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refreshPrinter()
        lockTask.refresh() // the device's screen lock, Home app and pinning settings may have changed since
        viewModelScope.launch { lockTask.state.collect { lock -> _state.update { it.copy(lock = lock) } } }
    }

    fun onEvent(event: AdminEvent) {
        when (event) {
            is AdminEvent.PinDigit -> _state.update {
                if (it.busy || it.authenticated || !event.digit.isDigit() || it.pin.length >= ADMIN_PIN_MAX_LENGTH) it
                else it.copy(pin = it.pin + event.digit, error = null)
            }
            AdminEvent.PinBackspace -> _state.update { if (it.busy) it else it.copy(pin = it.pin.dropLast(1), error = null) }
            AdminEvent.SubmitPin -> if (_state.value.canSubmitPin && !_state.value.authenticated) unlock()
            AdminEvent.TestPrint -> busy {
                when (val result = printReceipt.printTestPage()) {
                    is AppResult.Success -> _state.update { it.copy(messageRes = R.string.admin_test_print_ok) }
                    is AppResult.Failure -> _state.update { it.copy(error = result.error) }
                }
                refreshPrinter()
            }
            AdminEvent.Refresh -> busy {
                when (val result = configRepository.refresh()) {
                    is AppResult.Success -> {
                        coroutineScope {
                            launch { menuRepository.categories(forceRefresh = true) }
                            launch { menuRepository.products(forceRefresh = true) }
                        }
                        _state.update { it.copy(config = result.data, messageRes = R.string.admin_refreshed) }
                        refreshPrinter()
                    }
                    is AppResult.Failure -> _state.update { it.copy(error = result.error) }
                }
            }
            AdminEvent.Unpair -> busy {
                auth.forgetPairing()
                _effects.send(AdminEffect.Unpaired)
            }
            AdminEvent.AllowUsbPrinter -> if (_state.value.authenticated && _state.value.canAskForUsbPermission) {
                _state.update { it.copy(error = null, messageRes = null) }
                printReceipt.requestUsbPermission()
            }
            is AdminEvent.UsbPaperChosen -> if (_state.value.authenticated) {
                busy {
                    printReceipt.setUsbPaperWidth(event.widthDots)
                    refreshPrinter()
                }
            }
            AdminEvent.Resumed -> refreshPrinter()
        }
    }

    private fun refreshPrinter() {
        viewModelScope.launch {
            val printer = printReceipt.currentPrinter()
            _state.update { it.copy(printer = printer) }
        }
    }

    /** Temporary: the lock returns by itself once the kiosk is back on its first screen. */
    fun exitLockTask(activity: Activity) {
        if (!_state.value.authenticated) return
        _state.update { it.copy(error = null, messageRes = null) }
        lockTask.suspend(activity)
    }

    /** "Lock again" after a staff exit, or "Pin app" when it isn't pinned (Android then asks to confirm). */
    fun lockNow(activity: Activity) {
        if (!_state.value.authenticated) return
        val mode = _state.value.lock.mode
        if (mode != LockMode.Suspended && mode != LockMode.NotPinned) return
        _state.update { it.copy(error = null, messageRes = null) }
        lockTask.requestLock(activity)
    }

    /** Hands the device back (no more lock, the app can be uninstalled); setting it up again needs a computer. */
    fun releaseDeviceOwner(activity: Activity) {
        if (!_state.value.authenticated) return
        val released = lockTask.releaseDeviceOwner(activity)
        _state.update {
            it.copy(error = null, messageRes = if (released) R.string.admin_device_owner_released else R.string.admin_device_owner_release_failed)
        }
    }

    private fun unlock() = busy {
        // The PIN never stays on screen or in memory longer than the request, right or wrong.
        val pin = _state.value.pin
        when (val result = auth.unlockAdmin(pin)) {
            is AppResult.Success -> _state.update { it.copy(authenticated = true, staffName = result.data, pin = "") }
            is AppResult.Failure -> _state.update { it.copy(error = result.error, pin = "") }
        }
    }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, messageRes = null) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
