package com.pos.kiosk.feature.payment

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import com.pos.kiosk.domain.usecase.ObservePaymentStatusUseCase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class PromptPayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val payments = mockk<PaymentRepository>()
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val configState = MutableStateFlow<KioskConfig?>(null)
    private val configRepository = mockk<ConfigRepository> { every { config } returns configState }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // toRoute() decodes through an android Bundle, which is only a stub in JVM unit tests.
        mockkStatic(NAV_ROUTE_FACADE)
        every { any<SavedStateHandle>().toRoute(any<KClass<KioskRoute.PromptPay>>(), any()) } returns KioskRoute.PromptPay("order-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(NAV_ROUTE_FACADE)
    }

    private fun TestScope.viewModel() = PromptPayViewModel(
        savedStateHandle = SavedStateHandle(),
        payments = payments,
        orders = orders,
        session = InMemoryOrderSessionRepository(),
        observePayment = ObservePaymentStatusUseCase(payments) { testScheduler.currentTime },
        clock = { testScheduler.currentTime },
        configRepository = configRepository,
    )

    @Test
    fun `taps while a QR is being created never create a second charge`() = runTest(dispatcher) {
        // The gateway is slow and then fails, so the screen ends in EXPIRED (no polling loop left running).
        coEvery { payments.createPromptPay("order-1") } coAnswers {
            delay(1_000)
            AppResult.Failure(AppError.Network)
        }
        val viewModel = viewModel()
        dispatcher.scheduler.runCurrent() // init starts creating the first QR

        viewModel.newQr()
        viewModel.newQr()
        viewModel.payAtCounter()
        advanceUntilIdle()

        coVerify(exactly = 1) { payments.createPromptPay("order-1") }
        coVerify(exactly = 0) { orders.payAtCounter(any()) }
        assertEquals(PromptPayPhase.EXPIRED, viewModel.state.value.phase)

        // Once the first attempt has finished, "new QR" works again.
        viewModel.newQr()
        advanceUntilIdle()
        coVerify(exactly = 2) { payments.createPromptPay("order-1") }
    }

    @Test
    fun `no pay-at-counter fallback when the branch turned cash off`() = runTest(dispatcher) {
        configState.value = mockk<KioskConfig> { every { cashEnabled } returns false }
        coEvery { payments.createPromptPay("order-1") } returns AppResult.Failure(AppError.Network)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.payAtCounterAllowed)
        viewModel.payAtCounter()
        advanceUntilIdle()

        coVerify(exactly = 0) { orders.payAtCounter(any()) }
    }
}

private const val NAV_ROUTE_FACADE = "androidx.navigation.SavedStateHandleKt"
