package com.pos.kiosk.core.common

sealed interface AppError {
    data object Network : AppError
    data object InvalidCredentials : AppError
    data object NotAllowed : AppError
    /** Kiosk token missing/expired/revoked — the device must be set up (paired) again. */
    data object TerminalRevoked : AppError
    data object TerminalDisabled : AppError
    /** Refused at checkout: not enough stock, or switched off since the menu loaded. Ids name the cart line when known. */
    data class OutOfStock(val skuId: String?, val productId: String? = null) : AppError

    /** The product open in the option sheet sold out (or was switched off) while the customer was choosing. */
    data object ProductSoldOut : AppError
    data object OutsideHours : AppError
    data object OrderTypeUnavailable : AppError
    data object PromptPayUnavailable : AppError
    /** The branch turned this payment method off for its kiosks (pos-web /kiosk-settings) after the screen loaded. */
    data object PaymentMethodDisabled : AppError
    /** The shop's plan (or its entitlement override) doesn't include the kiosk: `enable_kiosk` is off. */
    data object KioskNotEnabled : AppError
    data object AlreadyPaid : AppError
    data object OrderInKitchen : AppError
    data object NoKioskTerminal : AppError
    data object PrinterUnavailable : AppError
    /** Staff PIN for the admin menu is wrong, or that staff member may not use the kiosk (deliberately one answer). */
    data object InvalidPin : AppError
    /** The owners' default PIN 0000, which never opens a kiosk. */
    data object WeakPin : AppError
    /** Too many wrong PINs on this kiosk; the PIN pad is locked for a while. */
    data object TooManyPinAttempts : AppError
    data class Server(val code: String?, val message: String?) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
