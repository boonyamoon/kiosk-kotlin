package com.pos.kiosk.feature.success

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.usecase.PrintReceiptUseCase
import com.pos.kiosk.navigation.KioskRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PrintStatus { PRINTING, PRINTED, FAILED, OUT_OF_PAPER, NO_PRINTER }

data class SuccessUiState(
    val queueNumber: String? = null,
    val isPaid: Boolean = false,
    val total: Double = 0.0,
    val printStatus: PrintStatus = PrintStatus.PRINTING,
)

@HiltViewModel
class SuccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: OrderRepository,
    private val session: OrderSessionRepository,
    private val printReceipt: PrintReceiptUseCase,
) : ViewModel() {

    private val orderId = savedStateHandle.toRoute<KioskRoute.Success>().orderId

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SuccessUiState> = _state.asStateFlow()

    /**
     * The once-a-second countdown lives apart from [state], so ticking only recomposes the small
     * "back to start in N s" text instead of the whole screen. Declared before init, which starts it.
     */
    private val _secondsLeft = MutableStateFlow(COUNTDOWN_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    private var receipt: Receipt? = null
    private var countdownJob: Job? = null
    private var printJob: Job? = null

    init {
        startPrint()
        startCountdown()
    }

    fun printAgain() {
        startCountdown()
        startPrint()
    }

    /** A tap while a print is still running is ignored: two sockets to one printer fail or print twice. */
    private fun startPrint() {
        if (printJob?.isActive == true) return
        printJob = viewModelScope.launch { print() }
    }

    fun finish() {
        countdownJob?.cancel()
        session.reset()
        _done.trySend(Unit)
    }

    /** The order is already placed; a missing receipt only affects printing, never the flow. */
    private suspend fun loadReceipt(): Receipt? {
        receipt?.let { return it }
        val loaded = (orders.receipt(orderId) as? AppResult.Success)?.data ?: return null
        receipt = loaded
        _state.update { it.copy(queueNumber = loaded.queueNumber ?: it.queueNumber, isPaid = loaded.isPaid, total = loaded.total) }
        return loaded
    }

    /** Also what "print again" runs, so a receipt that failed to load the first time is fetched again. */
    private suspend fun print() {
        val hasPrinter = printReceipt.hasPrinter()
        if (hasPrinter) _state.update { it.copy(printStatus = PrintStatus.PRINTING) }
        // Always load it: even without a printer the receipt carries the server's queue number and paid state.
        val current = loadReceipt()
        val status = when {
            !hasPrinter -> PrintStatus.NO_PRINTER
            current == null -> PrintStatus.FAILED
            else -> when (val result = printReceipt(current)) {
                is AppResult.Success -> PrintStatus.PRINTED
                is AppResult.Failure -> if (result.error == AppError.PrinterOutOfPaper) PrintStatus.OUT_OF_PAPER else PrintStatus.FAILED
            }
        }
        _state.update { it.copy(printStatus = status) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                _secondsLeft.value = left
                delay(1_000)
            }
            finish()
        }
    }

    private fun initialState(): SuccessUiState {
        val placed = session.session.value.placedOrder
        return SuccessUiState(
            queueNumber = placed?.queueNumber,
            isPaid = placed?.paymentMethod == PaymentMethod.PROMPTPAY,
            total = placed?.totalAmount ?: 0.0,
        )
    }

    companion object {
        const val COUNTDOWN_SECONDS = 20
    }
}
