package com.pos.kiosk.feature.payment

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.usecase.PlaceOrderUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentMethodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = InMemoryOrderSessionRepository()
    private val config = mockk<ConfigRepository> { every { config } returns MutableStateFlow<KioskConfig?>(null) }
    private val placeOrder = mockk<PlaceOrderUseCase>()
    private val unavailable = MutableStateFlow<Set<String>>(emptySet())
    private val menu = mockk<MenuRepository>(relaxed = true) {
        every { unavailableProductIds } returns unavailable
        every { markUnavailable(any()) } answers { unavailable.value = unavailable.value + firstArg<String>() }
    }

    private val latte = CartItem(
        lineId = "l1", productId = "p-latte", productName = "Latte", skuId = "sku-latte", skuName = "Hot",
        unitPrice = 50.0, quantity = 2, options = emptyList(), note = null, imageUrl = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        session.addItem(latte)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PaymentMethodViewModel(config, session, placeOrder, menu)

    @Test
    fun `an item switched off since the menu loaded sends the customer back to the cart`() = runTest(dispatcher) {
        coEvery { placeOrder(PaymentMethod.CASH) } returns AppResult.Failure(AppError.OutOfStock(skuId = "sku-latte", productId = "p-latte"))
        val viewModel = viewModel()

        viewModel.select(PaymentMethod.CASH)
        advanceUntilIdle()

        assertEquals(PaymentMethodEffect.BackToCart, withTimeoutOrNull(1_000) { viewModel.effects.first() })
        assertEquals(setOf("p-latte"), unavailable.value)
    }

    @Test
    fun `too little stock with some left keeps the customer here with the message`() = runTest(dispatcher) {
        coEvery { placeOrder(PaymentMethod.CASH) } returns AppResult.Failure(AppError.OutOfStock(skuId = "sku-latte"))
        coEvery { menu.refreshAvailability() } returns AppResult.Success(emptySet())
        val viewModel = viewModel()

        viewModel.select(PaymentMethod.CASH)
        advanceUntilIdle()

        assertNull(withTimeoutOrNull(1_000) { viewModel.effects.first() })
        assertEquals(AppError.OutOfStock(skuId = "sku-latte"), viewModel.state.value.error)
    }
}
