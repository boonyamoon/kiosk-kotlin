package com.pos.kiosk.feature.success

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.usecase.PrintReceiptUseCase
import com.pos.kiosk.navigation.KioskRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class SuccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val orders = mockk<OrderRepository>()
    private val printReceipt = mockk<PrintReceiptUseCase>()
    private val receipt = mockk<Receipt>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // toRoute() decodes through an android Bundle, which is only a stub in JVM unit tests.
        mockkStatic(NAV_ROUTE_FACADE)
        every { any<SavedStateHandle>().toRoute(any<KClass<KioskRoute.Success>>(), any()) } returns KioskRoute.Success("order-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(NAV_ROUTE_FACADE)
    }

    @Test
    fun `print again while a print is running does not open a second print`() = runTest(dispatcher) {
        every { printReceipt.hasPrinter() } returns true
        coEvery { orders.receipt("order-1") } returns AppResult.Success(receipt)
        coEvery { printReceipt(receipt) } coAnswers {
            delay(3_000) // a slow printer
            AppResult.Success(Unit)
        }
        val viewModel = SuccessViewModel(SavedStateHandle(), orders, InMemoryOrderSessionRepository(), printReceipt)
        dispatcher.scheduler.runCurrent() // first print starts

        viewModel.printAgain()
        viewModel.printAgain()
        advanceTimeBy(3_500)

        coVerify(exactly = 1) { printReceipt(receipt) }
        coVerify(exactly = 1) { orders.receipt("order-1") }
        assertEquals(PrintStatus.PRINTED, viewModel.state.value.printStatus)

        // After it finished, "print again" prints again (reusing the receipt it already loaded).
        viewModel.printAgain()
        advanceTimeBy(3_500)
        coVerify(exactly = 2) { printReceipt(receipt) }
        coVerify(exactly = 1) { orders.receipt("order-1") }
    }
}

private const val NAV_ROUTE_FACADE = "androidx.navigation.SavedStateHandleKt"
