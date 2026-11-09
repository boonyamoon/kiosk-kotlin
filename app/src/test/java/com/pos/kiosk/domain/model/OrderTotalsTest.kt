package com.pos.kiosk.domain.model

import com.pos.kiosk.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected values are what pos-services BranchOrderingService computes for the same cart. */
class OrderTotalsTest {
    private fun line(id: String, price: Double, quantity: Int = 1, serviceCharge: Boolean = false) =
        TestData.cartItem(lineId = id, skuId = "sku-$id", quantity = quantity).copy(unitPrice = price, serviceCharge = serviceCharge)

    private fun cart(vararg lines: CartItem) = Cart(lines.toList())

    @Test
    fun `VAT on top of prices that exclude it`() {
        val totals = cart(line("a", 100.0)).estimateTotals(taxRate = 7.0, taxIncluded = false, serviceChargeRate = 0.0)
        assertEquals(100.0, totals.subtotal, 0.0)
        assertEquals(7.0, totals.tax, 0.0)
        assertEquals(107.0, totals.total, 0.0)
        assertTrue(totals.hasExtras)
    }

    @Test
    fun `VAT taken out of prices that include it`() {
        val totals = cart(line("a", 107.0)).estimateTotals(taxRate = 7.0, taxIncluded = true, serviceChargeRate = 0.0)
        assertEquals(7.0, totals.tax, 0.0)
        assertEquals(107.0, totals.total, 0.0)
        assertFalse("nothing is added on top", totals.hasExtras)
    }

    @Test
    fun `service charge only on flagged products, VAT on food plus service charge`() {
        val totals = cart(line("a", 100.0, serviceCharge = true), line("b", 50.0))
            .estimateTotals(taxRate = 7.0, taxIncluded = false, serviceChargeRate = 10.0)
        assertEquals(150.0, totals.subtotal, 0.0)
        assertEquals(10.0, totals.serviceCharge, 0.0)
        assertEquals(11.2, totals.tax, 0.0) // 7 % of 160
        assertEquals(171.2, totals.total, 0.0)
        assertEquals(160.0, totals.beforeTax, 0.0) // the payment screen's "before VAT" line
        assertTrue(totals.vatAddedOnTop)
    }

    @Test
    fun `service charge with VAT-inclusive prices adds to the total, VAT stays inside it`() {
        val totals = cart(line("a", 100.0, quantity = 2, serviceCharge = true))
            .estimateTotals(taxRate = 7.0, taxIncluded = true, serviceChargeRate = 10.0)
        assertEquals(20.0, totals.serviceCharge, 0.0)
        assertEquals(14.39, totals.tax, 0.0) // 220 − 220 / 1.07
        assertEquals(220.0, totals.total, 0.0)
        assertFalse(totals.vatAddedOnTop) // VAT already in the prices: no VAT line on the payment screen
        assertTrue(totals.hasExtras)
    }

    @Test
    fun `rounds to satang at the same points as the server`() {
        val totals = cart(line("a", 33.33)).estimateTotals(taxRate = 7.0, taxIncluded = false, serviceChargeRate = 0.0)
        assertEquals(2.33, totals.tax, 0.0) // 2.3331
        assertEquals(35.66, totals.total, 0.0)
    }
}
