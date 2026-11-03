package com.pos.kiosk.domain.usecase

import com.pos.kiosk.TestData
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.OptionChoice
import com.pos.kiosk.domain.model.OptionGroup
import com.pos.kiosk.domain.model.SelectedOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionSelectionRulesTest {
    private val rules = OptionSelectionRules()

    @Test
    fun `defaults pick the default option of each group`() {
        val defaults = rules.defaults(TestData.milkTea.optionGroups)
        assertEquals(setOf("o-50"), defaults["g-sweet"])
        assertEquals(emptySet<String>(), defaults["g-top"])
    }

    @Test
    fun `single choice group replaces the selection and a required one cannot be cleared`() {
        val group = TestData.sweetness
        assertEquals(setOf("o-0"), rules.toggle(group, setOf("o-50"), "o-0"))
        assertEquals(setOf("o-0"), rules.toggle(group, setOf("o-0"), "o-0"))
    }

    @Test
    fun `multi choice group stops adding at max selections`() {
        val group = TestData.toppings
        val two = rules.toggle(group, rules.toggle(group, emptySet(), "t-pearl"), "t-jelly")
        assertEquals(setOf("t-pearl", "t-jelly"), two)
        assertEquals(two, rules.toggle(group, two, "t-pudding"))
        assertEquals(setOf("t-jelly"), rules.toggle(group, two, "t-pearl"))
    }

    @Test
    fun `required group without a selection is invalid`() {
        val invalid = rules.invalidGroups(TestData.milkTea.optionGroups, mapOf("g-sweet" to emptySet()))
        assertEquals(setOf("g-sweet"), invalid)
    }

    @Test
    fun `group needing more picks than it has options never blocks the order`() {
        fun choice(id: String) = OptionChoice(id, id, null, 0.0, false)
        val tooFew = OptionGroup("g", "g", null, isRequired = true, minSelections = 3, maxSelections = null, options = listOf(choice("a"), choice("b")))
        assertEquals(2, tooFew.effectiveMin)
        assertEquals(emptySet<String>(), rules.invalidGroups(listOf(tooFew), mapOf("g" to setOf("a", "b"))))
        val empty = OptionGroup("e", "e", null, isRequired = true, minSelections = 1, maxSelections = 1, options = emptyList())
        assertEquals(0, empty.effectiveMin)
        val minAboveMax = OptionGroup("m", "m", null, isRequired = false, minSelections = 3, maxSelections = 2, options = listOf(choice("a"), choice("b"), choice("c")))
        assertEquals(2, minAboveMax.effectiveMin)
    }

    @Test
    fun `selection below the minimum or above the maximum is invalid, within range is valid`() {
        val group = TestData.toppings
        assertEquals(emptySet<String>(), rules.invalidGroups(listOf(group), mapOf(group.id to setOf("t-pearl"))))
        val over = group.options.map { it.id }.toSet()
        if (group.maxSelections != null && over.size > group.maxSelections) {
            assertEquals(setOf(group.id), rules.invalidGroups(listOf(group), mapOf(group.id to over)))
        }
    }
}

class AddToCartUseCaseTest {
    private val session = InMemoryOrderSessionRepository()
    private val addToCart = AddToCartUseCase(session, OptionSelectionRules())

    @Test
    fun `adds the item with chosen options and their prices`() {
        val added = addToCart(TestData.milkTea, "s-l", mapOf("g-sweet" to setOf("o-0"), "g-top" to setOf("t-pearl")), 2, " no ice ")

        assertTrue(added)
        val item = session.session.value.cart.items.single()
        assertEquals("s-l", item.skuId)
        assertEquals(55.0, item.unitPrice, 0.0)
        assertEquals(listOf("o-0", "t-pearl"), item.options.map { it.optionId })
        assertEquals(65.0, item.unitTotal, 0.0)
        assertEquals(130.0, item.lineTotal, 0.0)
        assertEquals("no ice", item.note)
    }

    @Test
    fun `carries the product's service-charge flag onto the cart line`() {
        addToCart(TestData.milkTea.copy(serviceCharge = true), "s-m", mapOf("g-sweet" to setOf("o-0")), 1, null)
        assertTrue(session.session.value.cart.items.single().serviceCharge)
    }

    @Test
    fun `refuses an invalid selection`() {
        assertFalse(addToCart(TestData.milkTea, "s-m", mapOf("g-sweet" to emptySet()), 1, null))
        assertTrue(session.session.value.cart.isEmpty)
    }

    @Test
    fun `refuses an unavailable product`() {
        assertFalse(addToCart(TestData.milkTea.copy(isAvailable = false), "s-m", mapOf("g-sweet" to setOf("o-0")), 1, null))
    }
}

class InMemoryOrderSessionRepositoryTest {
    private val session = InMemoryOrderSessionRepository()
    private val pearl = SelectedOption("g-top", "t-pearl", "ไข่มุก", 10.0)

    @Test
    fun `identical configurations merge into one line`() {
        session.addItem(TestData.cartItem(lineId = "a", options = listOf(pearl)))
        session.addItem(TestData.cartItem(lineId = "b", options = listOf(pearl), quantity = 2))

        val cart = session.session.value.cart
        assertEquals(1, cart.items.size)
        assertEquals(3, cart.itemCount)
        assertEquals(165.0, cart.subtotal, 0.0)
    }

    @Test
    fun `different options or notes stay separate lines`() {
        session.addItem(TestData.cartItem(lineId = "a"))
        session.addItem(TestData.cartItem(lineId = "b", options = listOf(pearl)))
        session.addItem(TestData.cartItem(lineId = "c", note = "less ice"))
        assertEquals(3, session.session.value.cart.items.size)
    }

    @Test
    fun `a product's units add up across its option variants`() {
        session.addItem(TestData.cartItem(lineId = "a", quantity = 2))
        session.addItem(TestData.cartItem(lineId = "b", options = listOf(pearl)))
        // Another product has its own SKU (lines merge by SKU + options + note).
        session.addItem(TestData.cartItem(lineId = "c", skuId = "s-other", quantity = 4).copy(productId = "p-other"))

        assertEquals(mapOf("p-milk-tea" to 3, "p-other" to 4), session.session.value.cart.quantitiesByProduct())
    }

    @Test
    fun `quantity zero removes the line`() {
        session.addItem(TestData.cartItem(lineId = "a"))
        session.updateQuantity("a", 0)
        assertTrue(session.session.value.cart.isEmpty)
    }

    @Test
    fun `checkout attempt is reused for the same fingerprint and renewed after clearCheckout`() {
        val first = session.checkoutAttemptFor("fp")
        assertEquals(first, session.checkoutAttemptFor("fp"))
        assertTrue(first.idempotencyKey != session.checkoutAttemptFor("other").idempotencyKey)

        val again = session.checkoutAttemptFor("fp")
        session.clearCheckout()
        assertTrue(again.idempotencyKey != session.checkoutAttemptFor("fp").idempotencyKey)
    }

    @Test
    fun `reset clears everything`() {
        session.addItem(TestData.cartItem())
        session.checkoutAttemptFor("fp")
        session.reset()
        val state = session.session.value
        assertTrue(state.cart.isEmpty)
        assertEquals(null, state.checkout)
        assertEquals(null, state.member)
    }
}
