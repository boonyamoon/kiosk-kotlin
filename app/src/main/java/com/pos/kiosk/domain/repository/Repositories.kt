package com.pos.kiosk.domain.repository

import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.CheckoutAttempt
import com.pos.kiosk.domain.model.KioskAccess
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.KioskTerminalOption
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.OrderSession
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PairedTerminal
import com.pos.kiosk.domain.model.PaymentState
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.model.Product
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.PromptPayCharge
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.model.StaffSession
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    suspend fun staffLogin(email: String, password: String): AppResult<StaffSession>
    suspend fun kioskAccess(staff: StaffSession): AppResult<KioskAccess>
    suspend fun kioskTerminals(staff: StaffSession, branchId: String): AppResult<List<KioskTerminalOption>>
    /** Pairs this device and persists the kiosk token on success. */
    suspend fun pair(staff: StaffSession, branchId: String, terminalId: String?, deviceName: String): AppResult<PairedTerminal>
    suspend fun pairedTerminal(): PairedTerminal?
    suspend fun tokenLifetime(): TokenLifetime?
    suspend fun refreshToken(): AppResult<Unit>
    suspend fun forgetPairing()
    /** Opens the hidden staff menu with a staff PIN; returns the staff member's name. */
    suspend fun unlockAdmin(pin: String): AppResult<String>
}

data class TokenLifetime(val issuedAtMillis: Long, val expiresAtMillis: Long)

interface ConfigRepository {
    val config: StateFlow<KioskConfig?>
    suspend fun refresh(): AppResult<KioskConfig>
}

interface MenuRepository {
    suspend fun categories(forceRefresh: Boolean = false): AppResult<List<Category>>
    suspend fun products(forceRefresh: Boolean = false): AppResult<List<Product>>
    suspend fun productDetail(productId: String): AppResult<ProductDetail>
    fun isStale(): Boolean

    /**
     * Products that can't be ordered right now. Set by every full product load and replaced by every
     * [refreshAvailability], so it is never older than the last poll (~20 s on the ordering screens).
     */
    val unavailableProductIds: StateFlow<Set<String>>

    /** The light availability poll; a failure (offline) leaves the last known set as it is. */
    suspend fun refreshAvailability(): AppResult<Set<String>>

    /** The server just refused this product at checkout: mark it now, before the next poll confirms. */
    fun markUnavailable(productId: String)
}

interface MemberRepository {
    /** Success(null) = no such member; the customer continues as guest. */
    suspend fun lookup(phone: String): AppResult<Member?>
}

interface OrderSessionRepository {
    val session: StateFlow<OrderSession>
    fun setOrderType(orderType: OrderType)
    fun setMember(member: Member?)
    fun addItem(item: CartItem)
    fun updateQuantity(lineId: String, quantity: Int)
    fun removeItem(lineId: String)
    fun checkoutAttemptFor(fingerprint: String): CheckoutAttempt
    fun setPlacedOrder(order: PlacedOrder?)
    /** After a cancelled order: the same cart must get a NEW idempotency key or the server replays the cancelled order. */
    fun clearCheckout()
    fun reset()
}

interface OrderRepository {
    suspend fun placeOrder(idempotencyKey: String, request: PlaceOrderRequest): AppResult<PlacedOrder>
    suspend fun receipt(orderId: String): AppResult<Receipt>
    suspend fun cancel(orderId: String): AppResult<Unit>
    suspend fun payAtCounter(orderId: String): AppResult<Unit>
}

interface PaymentRepository {
    suspend fun createPromptPay(orderId: String): AppResult<PromptPayCharge>
    suspend fun status(orderId: String): AppResult<PaymentState>
    suspend fun cancelPending(orderId: String): AppResult<Unit>
}
