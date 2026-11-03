package com.pos.kiosk.domain.model

import com.pos.kiosk.core.common.roundMoney

enum class OrderType { DINE_IN, TAKE_AWAY }

enum class PaymentMethod { CASH, PROMPTPAY }

data class Member(val customerId: String, val displayName: String, val availablePoints: Int)

data class SelectedOption(
    val groupId: String,
    val optionId: String,
    val name: String,
    val priceAdjustment: Double,
)

data class CartItem(
    val lineId: String,
    val productId: String,
    val productName: String,
    val skuId: String,
    val skuName: String,
    val unitPrice: Double,
    val quantity: Int,
    val options: List<SelectedOption>,
    val note: String?,
    val imageUrl: String?,
    /** Line total before any discount, when the menu supplies one. The menu API has none yet, so this stays null. */
    val preDiscountTotal: Double? = null,
    /** The product counts towards the service charge (the server decides per product too). */
    val serviceCharge: Boolean = false,
) {
    val unitTotal: Double get() = roundMoney(unitPrice + options.sumOf { it.priceAdjustment })
    val lineTotal: Double get() = roundMoney(unitTotal * quantity)

    fun sameConfigurationAs(other: CartItem): Boolean =
        skuId == other.skuId &&
            options.map { it.optionId }.toSet() == other.options.map { it.optionId }.toSet() &&
            note.orEmpty().trim() == other.note.orEmpty().trim()
}

data class Cart(val items: List<CartItem> = emptyList()) {
    val itemCount: Int get() = items.sumOf { it.quantity }
    /** Client-side estimate only — tax/service charge come from the server's order response. */
    val subtotal: Double get() = roundMoney(items.sumOf { it.lineTotal })
    val isEmpty: Boolean get() = items.isEmpty()

    /** Units of each product in the cart, all option variants together. */
    fun quantitiesByProduct(): Map<String, Int> = items.groupingBy { it.productId }.fold(0) { total, item -> total + item.quantity }
}

/** Everything one customer's visit accumulates; wiped by OrderSessionRepository.reset(). */
data class OrderSession(
    val orderType: OrderType = OrderType.DINE_IN,
    val member: Member? = null,
    val cart: Cart = Cart(),
    val placedOrder: PlacedOrder? = null,
    val checkout: CheckoutAttempt? = null,
)

/** Same request → same Idempotency-Key, so a retry after a timeout can't create a second order. */
data class CheckoutAttempt(val fingerprint: String, val idempotencyKey: String)

data class PlaceOrderRequest(
    val orderType: OrderType,
    val paymentMethod: PaymentMethod,
    val customerId: String?,
    val items: List<CartItem>,
)

data class PlacedOrder(
    val orderId: String,
    val orderNumber: String,
    val queueNumber: String?,
    val totalAmount: Double,
    val paymentMethod: PaymentMethod,
    val kitchenDispatched: Boolean,
)

data class PromptPayCharge(
    val orderId: String,
    val amount: Double,
    val qrImageBase64: String,
    val expiresAtMillis: Long,
)

data class PaymentState(val paid: Boolean, val orderStatus: String, val paymentStatus: String?)

data class Receipt(
    val orderId: String,
    val orderNumber: String,
    val queueNumber: String?,
    val orderType: OrderType,
    val isPaid: Boolean,
    val paymentMethod: PaymentMethod?,
    val createdAtMillis: Long,
    val memberName: String?,
    val items: List<ReceiptItem>,
    val subtotal: Double,
    val discount: Double,
    val serviceChargeRate: Double,
    val serviceCharge: Double,
    val taxRate: Double,
    val taxIncluded: Boolean,
    val taxAmount: Double,
    val total: Double,
    /** The receipt's own running number when the backend issued one; the order number otherwise. */
    val receiptNumber: String? = null,
    /** Satang rounding the till applied, shown as its own line when not zero. */
    val rounding: Double = 0.0,
    val paidAmount: Double = 0.0,
) {
    /** Net amount before VAT (VAT inclusive: taken out of the total; exclusive: food + service charge). */
    val beforeTax: Double get() = roundMoney(total - rounding - taxAmount)
}

data class ReceiptItem(
    val name: String,
    val skuName: String?,
    val quantity: Double,
    val lineTotal: Double,
    val note: String?,
    val options: List<String>,
)
