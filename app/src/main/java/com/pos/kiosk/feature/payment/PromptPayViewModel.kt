package com.pos.kiosk.feature.payment

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.Clock
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import com.pos.kiosk.domain.usecase.ObservePaymentStatusUseCase
import com.pos.kiosk.domain.usecase.PromptPayProgress
import com.pos.kiosk.navigation.KioskRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PromptPayPhase { CREATING, SHOWING, EXPIRED, WORKING }

data class PromptPayUiState(
    val phase: PromptPayPhase = PromptPayPhase.CREATING,
    val qr: ImageBitmap? = null,
    val amount: Double = 0.0,
    val error: AppError? = null,
    /** "Pay at the counter instead" is paying cash: not offered when the branch turned cash off for its kiosks. */
    val payAtCounterAllowed: Boolean = true,
)

sealed interface PromptPayEffect {
    data class Completed(val orderId: String) : PromptPayEffect
    data object BackToCart : PromptPayEffect
    data object Abandoned : PromptPayEffect
}

@HiltViewModel
class PromptPayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentRepository,
    private val orders: OrderRepository,
    private val session: OrderSessionRepository,
    private val observePayment: ObservePaymentStatusUseCase,
    private val clock: Clock,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val orderId = savedStateHandle.toRoute<KioskRoute.PromptPay>().orderId

    private val _state = MutableStateFlow(PromptPayUiState(payAtCounterAllowed = configRepository.config.value?.cashEnabled ?: true))
    val state: StateFlow<PromptPayUiState> = _state.asStateFlow()

    /** Ticks every second while a QR is shown; kept apart from [state] so only the countdown text recomposes. */
    private val _secondsLeft = MutableStateFlow(0L)
    val secondsLeft: StateFlow<Long> = _secondsLeft.asStateFlow()

    private val _effects = Channel<PromptPayEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var watchJob: Job? = null
    private var abandonJob: Job? = null
    private var actionJob: Job? = null

    init {
        createQr()
    }

    fun newQr() = createQr()

    fun payAtCounter() {
        if (!_state.value.payAtCounterAllowed) return
        runAction {
            when (val result = orders.payAtCounter(orderId)) {
                is AppResult.Success -> _effects.send(PromptPayEffect.Completed(orderId))
                is AppResult.Failure -> when (result.error) {
                    AppError.AlreadyPaid -> _effects.send(PromptPayEffect.Completed(orderId))
                    else -> _state.update { it.copy(phase = PromptPayPhase.EXPIRED, error = result.error) }
                }
            }
        }
    }

    /** Customer backs out: cancel the QR and the HELD order, keep the cart so they can change it. */
    fun cancelOrder() = runAction {
        payments.cancelPending(orderId)
        when (val result = orders.cancel(orderId)) {
            is AppResult.Success -> {
                session.clearCheckout()
                _effects.send(PromptPayEffect.BackToCart)
            }
            is AppResult.Failure ->
                if (result.error == AppError.AlreadyPaid) _effects.send(PromptPayEffect.Completed(orderId))
                else _state.update { it.copy(phase = PromptPayPhase.EXPIRED, error = result.error) }
        }
    }

    private fun createQr() = runAction(PromptPayPhase.CREATING) {
        when (val result = payments.createPromptPay(orderId)) {
            is AppResult.Success -> {
                val charge = result.data
                val bitmap = withContext(Dispatchers.Default) { decode(charge.qrImageBase64) }
                _secondsLeft.value = secondsUntil(charge.expiresAtMillis)
                _state.update { it.copy(phase = PromptPayPhase.SHOWING, qr = bitmap, amount = charge.amount, error = null) }
                watch(charge.expiresAtMillis)
            }
            is AppResult.Failure ->
                if (result.error == AppError.AlreadyPaid) _effects.send(PromptPayEffect.Completed(orderId))
                else _state.update { it.copy(phase = PromptPayPhase.EXPIRED, error = result.error) }
        }
    }

    private fun watch(expiresAtMillis: Long) {
        watchJob?.cancel()
        abandonJob?.cancel()
        watchJob = viewModelScope.launch {
            launch {
                while (isActive) {
                    _secondsLeft.value = secondsUntil(expiresAtMillis)
                    delay(1_000)
                }
            }
            observePayment(orderId, expiresAtMillis).collect { progress ->
                when (progress) {
                    PromptPayProgress.Waiting -> Unit
                    PromptPayProgress.Paid -> {
                        _effects.send(PromptPayEffect.Completed(orderId))
                        watchJob?.cancel()
                    }
                    PromptPayProgress.Expired -> {
                        _state.update { it.copy(phase = PromptPayPhase.EXPIRED, qr = null) }
                        payments.cancelPending(orderId)
                        startAbandonTimer()
                        watchJob?.cancel()
                    }
                }
            }
        }
    }

    /** Nobody touched the expired screen for a minute: release the order and start over. */
    private fun startAbandonTimer() {
        abandonJob?.cancel()
        abandonJob = viewModelScope.launch {
            delay(ABANDON_AFTER_MS)
            orders.cancel(orderId)
            _effects.send(PromptPayEffect.Abandoned)
        }
    }

    /**
     * One server action at a time: a double tap on "new QR" (or a tap while the first QR is still
     * being created) must not create a second charge for the same order.
     */
    private fun runAction(phase: PromptPayPhase = PromptPayPhase.WORKING, block: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        watchJob?.cancel()
        abandonJob?.cancel()
        actionJob = viewModelScope.launch {
            _state.update { it.copy(phase = phase, error = null) }
            block()
        }
    }

    private fun secondsUntil(millis: Long): Long = ((millis - clock.nowMillis()) / 1000).coerceAtLeast(0)

    private fun decode(base64: String): ImageBitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val ABANDON_AFTER_MS = 60_000L
    }
}
