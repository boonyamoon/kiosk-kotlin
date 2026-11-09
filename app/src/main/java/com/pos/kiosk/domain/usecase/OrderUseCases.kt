package com.pos.kiosk.domain.usecase

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.Clock
import com.pos.kiosk.core.common.getOrNull
import com.pos.kiosk.core.common.onSuccess
import com.pos.kiosk.core.printer.PrinterInfo
import com.pos.kiosk.core.printer.PrinterRouter
import com.pos.kiosk.core.printer.ReceiptRenderer
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaceOrderUseCase @Inject constructor(
    private val session: OrderSessionRepository,
    private val orders: OrderRepository,
) {
    /**
     * Retrying the same cart + payment method reuses the Idempotency-Key, so a request that timed
     * out but actually succeeded returns the original order instead of sending a second one to the kitchen.
     */
    suspend operator fun invoke(paymentMethod: PaymentMethod): AppResult<PlacedOrder> {
        val current = session.session.value
        if (current.cart.isEmpty) return AppResult.Failure(AppError.Server("EMPTY_CART", null))
        val request = PlaceOrderRequest(
            orderType = current.orderType,
            paymentMethod = paymentMethod,
            customerId = current.member?.customerId,
            items = current.cart.items,
        )
        val attempt = session.checkoutAttemptFor(fingerprint(request))
        return orders.placeOrder(attempt.idempotencyKey, request).onSuccess { session.setPlacedOrder(it) }
    }

    private fun fingerprint(request: PlaceOrderRequest): String = buildString {
        append(request.orderType).append('|').append(request.paymentMethod).append('|').append(request.customerId)
        request.items.sortedBy { it.lineId }.forEach { item ->
            append('|').append(item.skuId).append('x').append(item.quantity)
            append(item.options.map { it.optionId }.sorted()).append(item.note.orEmpty())
        }
    }
}

sealed interface PromptPayProgress {
    data object Waiting : PromptPayProgress
    data object Paid : PromptPayProgress
    data object Expired : PromptPayProgress
}

class ObservePaymentStatusUseCase @Inject constructor(
    private val payments: PaymentRepository,
    private val clock: Clock,
) {
    /** Polls until paid or the QR expires; transient network errors just keep polling. */
    operator fun invoke(orderId: String, expiresAtMillis: Long, pollIntervalMs: Long = 3_000): Flow<PromptPayProgress> = flow {
        emit(PromptPayProgress.Waiting)
        while (true) {
            val paid = payments.status(orderId).getOrNull()?.paid == true
            if (paid) {
                emit(PromptPayProgress.Paid)
                return@flow
            }
            if (clock.nowMillis() >= expiresAtMillis) {
                emit(PromptPayProgress.Expired)
                return@flow
            }
            delay(pollIntervalMs)
        }
    }
}

class PrintReceiptUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
    private val renderer: ReceiptRenderer,
    private val router: PrinterRouter,
) {
    /** Also reaches a built-in printer, which is why the first screen calls it: see AttractViewModel.onShown. */
    suspend fun currentPrinter(): PrinterInfo? = router.current(configRepository.config.value?.receiptPrinter)

    suspend fun hasPrinter(): Boolean = currentPrinter() != null

    fun requestUsbPermission() = router.requestUsbPermission()

    suspend fun setUsbPaperWidth(widthDots: Int) = router.setUsbPaperWidth(widthDots)

    suspend operator fun invoke(receipt: Receipt): AppResult<Unit> {
        val config = configRepository.config.value ?: configRepository.refresh().getOrNull()
            ?: return AppResult.Failure(AppError.PrinterUnavailable)
        val printer = router.current(config.receiptPrinter) ?: return AppResult.Failure(AppError.PrinterUnavailable)
        val image = withContext(Dispatchers.Default) { renderer.render(receipt, config, printer.paperWidthDots) }
        return router.print(printer, image, config.receiptPrinter?.copyCount ?: 1)
    }

    suspend fun printTestPage(): AppResult<Unit> {
        val config = configRepository.config.value ?: return AppResult.Failure(AppError.PrinterUnavailable)
        val printer = router.current(config.receiptPrinter) ?: return AppResult.Failure(AppError.PrinterUnavailable)
        val image = withContext(Dispatchers.Default) { renderer.renderTestPage(config, printer) }
        return router.print(printer, image)
    }
}
