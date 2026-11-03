package com.pos.kiosk.feature.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.Cart
import com.pos.kiosk.domain.model.OrderTotals
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.estimateTotals
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.usecase.PlaceOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentMethodUiState(
    /** Same arithmetic as the server, so this is what the order will charge. */
    val totals: OrderTotals = Cart().estimateTotals(taxRate = 0.0, taxIncluded = true, serviceChargeRate = 0.0),
    val cashEnabled: Boolean = true,
    val promptPayEnabled: Boolean = false,
    val placing: PaymentMethod? = null,
    val error: AppError? = null,
)

sealed interface PaymentMethodEffect {
    data class OrderPlaced(val order: PlacedOrder) : PaymentMethodEffect
    data object BackToCart : PaymentMethodEffect
}

@HiltViewModel
class PaymentMethodViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val session: OrderSessionRepository,
    private val placeOrder: PlaceOrderUseCase,
    private val menu: MenuRepository,
) : ViewModel() {

    private val config = configRepository.config.value
    private val _state = MutableStateFlow(
        PaymentMethodUiState(
            totals = session.session.value.cart.estimateTotals(
                taxRate = config?.taxRate ?: 0.0,
                taxIncluded = config?.taxIncluded ?: true,
                serviceChargeRate = config?.serviceChargeRate ?: 0.0,
            ),
            cashEnabled = config?.cashEnabled ?: true,
            promptPayEnabled = config?.promptPayEnabled ?: false,
        ),
    )
    val state: StateFlow<PaymentMethodUiState> = _state.asStateFlow()

    private val _effects = Channel<PaymentMethodEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** On a network error the customer retries the same method; PlaceOrderUseCase reuses the idempotency key. */
    fun select(method: PaymentMethod) {
        if (_state.value.placing != null) return
        viewModelScope.launch {
            _state.update { it.copy(placing = method, error = null) }
            when (val result = placeOrder(method)) {
                is AppResult.Success -> {
                    _state.update { it.copy(placing = null) }
                    _effects.send(PaymentMethodEffect.OrderPlaced(result.data))
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(placing = null, error = result.error) }
                    // Switched off in pos-web since this screen loaded: reload, so only what is still on stays.
                    if (result.error == AppError.PaymentMethodDisabled) refreshMethods()
                    (result.error as? AppError.OutOfStock)?.let { onOutOfStock(it) }
                }
            }
        }
    }

    /**
     * Refused for stock or availability: mark the product the server named, re-check availability, and when a cart
     * line is now sold out send the customer back to the cart, where it is flagged and must be removed. (Too little
     * stock for the quantity, with some left, keeps the message here instead.)
     */
    private suspend fun onOutOfStock(error: AppError.OutOfStock) {
        val cart = session.session.value.cart
        val productId = error.productId ?: cart.items.firstOrNull { it.skuId.equals(error.skuId, ignoreCase = true) }?.productId
        if (error.productId != null) menu.markUnavailable(error.productId)
        menu.refreshAvailability()
        val unavailable = menu.unavailableProductIds.value
        if (cart.items.any { it.productId in unavailable } || (productId != null && productId in unavailable)) {
            _effects.send(PaymentMethodEffect.BackToCart)
        }
    }

    private suspend fun refreshMethods() {
        val fresh = (configRepository.refresh() as? AppResult.Success)?.data ?: return
        _state.update { it.copy(cashEnabled = fresh.cashEnabled, promptPayEnabled = fresh.promptPayEnabled) }
    }

    fun back() {
        if (_state.value.placing == null) _effects.trySend(PaymentMethodEffect.BackToCart)
    }
}
