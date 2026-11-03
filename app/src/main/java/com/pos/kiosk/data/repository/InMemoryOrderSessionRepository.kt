package com.pos.kiosk.data.repository

import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.CheckoutAttempt
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.OrderSession
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.repository.OrderSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryOrderSessionRepository @Inject constructor() : OrderSessionRepository {
    private val _session = MutableStateFlow(OrderSession())
    override val session: StateFlow<OrderSession> = _session.asStateFlow()

    override fun setOrderType(orderType: OrderType) = _session.update { it.copy(orderType = orderType) }

    override fun setMember(member: Member?) = _session.update { it.copy(member = member) }

    override fun addItem(item: CartItem) = _session.update { session ->
        val items = session.cart.items
        val existing = items.firstOrNull { it.sameConfigurationAs(item) }
        val updated = if (existing != null) {
            items.map { if (it.lineId == existing.lineId) it.copy(quantity = (it.quantity + item.quantity).coerceAtMost(MAX_QUANTITY)) else it }
        } else {
            items + item
        }
        session.copy(cart = session.cart.copy(items = updated))
    }

    override fun updateQuantity(lineId: String, quantity: Int) = _session.update { session ->
        val updated = if (quantity <= 0) {
            session.cart.items.filterNot { it.lineId == lineId }
        } else {
            session.cart.items.map { if (it.lineId == lineId) it.copy(quantity = quantity.coerceAtMost(MAX_QUANTITY)) else it }
        }
        session.copy(cart = session.cart.copy(items = updated))
    }

    override fun removeItem(lineId: String) = updateQuantity(lineId, 0)

    /** Compare-and-create in one atomic update, so concurrent callers can never mint two keys for one cart. */
    override fun checkoutAttemptFor(fingerprint: String): CheckoutAttempt =
        _session.updateAndGet { session ->
            if (session.checkout?.fingerprint == fingerprint) {
                session
            } else {
                session.copy(checkout = CheckoutAttempt(fingerprint = fingerprint, idempotencyKey = UUID.randomUUID().toString()))
            }
        }.checkout!!

    override fun setPlacedOrder(order: PlacedOrder?) = _session.update { it.copy(placedOrder = order) }

    override fun clearCheckout() = _session.update { it.copy(placedOrder = null, checkout = null) }

    override fun reset() {
        _session.value = OrderSession()
    }

    private companion object {
        const val MAX_QUANTITY = 99
    }
}
