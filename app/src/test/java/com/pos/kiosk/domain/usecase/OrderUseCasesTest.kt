package com.pos.kiosk.domain.usecase

import app.cash.turbine.test
import com.pos.kiosk.TestData
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.PaymentState
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceOrderUseCaseTest {
    private val session = InMemoryOrderSessionRepository()
    private val orders = mockk<OrderRepository>()
    private val placeOrder = PlaceOrderUseCase(session, orders)

    private val placed = PlacedOrder("order-1", "KS-1", "Q001", 45.0, PaymentMethod.CASH, kitchenDispatched = true)

    @Test
    fun `sends the session cart, order type and member`() = runTest {
        session.setOrderType(OrderType.TAKE_AWAY)
        session.setMember(Member("c-1", "สมชาย ก.", 10))
        session.addItem(TestData.cartItem())
        val request = slot<PlaceOrderRequest>()
        coEvery { orders.placeOrder(any(), capture(request)) } returns AppResult.Success(placed)

        val result = placeOrder(PaymentMethod.CASH)

        assertEquals(AppResult.Success(placed), result)
        assertEquals(OrderType.TAKE_AWAY, request.captured.orderType)
        assertEquals("c-1", request.captured.customerId)
        assertEquals(placed, session.session.value.placedOrder)
    }

    @Test
    fun `a retry after a network error reuses the idempotency key`() = runTest {
        session.addItem(TestData.cartItem())
        val keys = mutableListOf<String>()
        coEvery { orders.placeOrder(capture(keys), any()) } returns AppResult.Failure(AppError.Network) andThen AppResult.Success(placed)

        placeOrder(PaymentMethod.CASH)
        placeOrder(PaymentMethod.CASH)

        assertEquals(2, keys.size)
        assertEquals(keys[0], keys[1])
    }

    @Test
    fun `changing the cart or payment method uses a new key`() = runTest {
        session.addItem(TestData.cartItem())
        val keys = mutableListOf<String>()
        coEvery { orders.placeOrder(capture(keys), any()) } returns AppResult.Failure(AppError.Network)

        placeOrder(PaymentMethod.CASH)
        placeOrder(PaymentMethod.PROMPTPAY)
        session.updateQuantity("line-1", 2)
        placeOrder(PaymentMethod.PROMPTPAY)

        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `an empty cart is never sent`() = runTest {
        val result = placeOrder(PaymentMethod.CASH)
        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { orders.placeOrder(any(), any()) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObservePaymentStatusUseCaseTest {
    private val payments = mockk<PaymentRepository>()

    @Test
    fun `emits Paid as soon as the payment completes`() = runTest {
        coEvery { payments.status("o") } returnsMany listOf(
            AppResult.Success(PaymentState(paid = false, orderStatus = "HELD", paymentStatus = "PENDING")),
            AppResult.Failure(AppError.Network),
            AppResult.Success(PaymentState(paid = true, orderStatus = "COMPLETED", paymentStatus = "COMPLETED")),
        )
        val observe = ObservePaymentStatusUseCase(payments) { testScheduler.currentTime }

        observe("o", expiresAtMillis = 60_000).test {
            assertEquals(PromptPayProgress.Waiting, awaitItem())
            assertEquals(PromptPayProgress.Paid, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 3) { payments.status("o") }
    }

    @Test
    fun `emits Expired once the QR expiry passes without payment`() = runTest {
        coEvery { payments.status("o") } returns AppResult.Success(PaymentState(paid = false, orderStatus = "HELD", paymentStatus = "PENDING"))
        val observe = ObservePaymentStatusUseCase(payments) { testScheduler.currentTime }

        observe("o", expiresAtMillis = 10_000, pollIntervalMs = 3_000).test {
            assertEquals(PromptPayProgress.Waiting, awaitItem())
            assertEquals(PromptPayProgress.Expired, awaitItem())
            awaitComplete()
        }
        assertTrue(testScheduler.currentTime >= 10_000)
    }

    @Test
    fun `a payment that lands exactly at expiry still counts as paid`() = runTest {
        coEvery { payments.status("o") } returns AppResult.Success(PaymentState(paid = true, orderStatus = "HELD", paymentStatus = "COMPLETED"))
        val observe = ObservePaymentStatusUseCase(payments) { 99_999 }

        observe("o", expiresAtMillis = 0).test {
            assertEquals(PromptPayProgress.Waiting, awaitItem())
            assertNotEquals(PromptPayProgress.Expired, awaitItem())
            awaitComplete()
        }
    }
}
