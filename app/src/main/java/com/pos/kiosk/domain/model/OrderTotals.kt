package com.pos.kiosk.domain.model

import com.pos.kiosk.core.common.roundMoney

/** What the order will cost: the food, the service charge and VAT, and what the customer pays. */
data class OrderTotals(
    val subtotal: Double,
    val serviceChargeRate: Double,
    val serviceCharge: Double,
    val taxRate: Double,
    val taxIncluded: Boolean,
    val tax: Double,
    val total: Double,
) {
    /** Anything on top of the food: a service charge, or VAT added to prices that exclude it. */
    val hasExtras: Boolean get() = serviceCharge > 0 || vatAddedOnTop

    /** Prices exclude VAT, so VAT is charged on top and shown as its own line. */
    val vatAddedOnTop: Boolean get() = !taxIncluded && tax > 0

    /** What VAT is charged on: food + service charge (the server's `taxable`). */
    val beforeTax: Double get() = roundMoney(subtotal + serviceCharge)
}

/**
 * Worked out exactly like pos-services BranchOrderingService.insertHeldOrderInTx — same order of operations and
 * the same rounding points — so the amount shown before choosing a payment method is the amount the order gets:
 * the service charge only on products flagged for it, VAT on subtotal + service charge, taken out of that when
 * prices include VAT and added on top when they don't.
 */
fun Cart.estimateTotals(taxRate: Double, taxIncluded: Boolean, serviceChargeRate: Double): OrderTotals {
    var subtotal = 0.0
    var chargeable = 0.0
    items.forEach { item ->
        subtotal = roundMoney(subtotal + item.lineTotal)
        if (item.serviceCharge) chargeable = roundMoney(chargeable + item.lineTotal)
    }
    val serviceCharge = roundMoney(chargeable * (serviceChargeRate / 100))
    val taxable = roundMoney(subtotal + serviceCharge)
    val tax: Double
    val total: Double
    if (taxIncluded) {
        tax = roundMoney(taxable - taxable / (1 + taxRate / 100))
        total = roundMoney(subtotal + serviceCharge)
    } else {
        tax = roundMoney(taxable * (taxRate / 100))
        total = roundMoney(subtotal + serviceCharge + tax)
    }
    return OrderTotals(subtotal, serviceChargeRate, serviceCharge, taxRate, taxIncluded, tax, total)
}
