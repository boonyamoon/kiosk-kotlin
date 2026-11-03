package com.pos.kiosk.core.network

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Backend envelope: `{ success, data }` or `{ success: false, code, message, data? }`. */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = true,
    val data: T? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val data: JsonObject? = null,
)

object ApiErrorMapper {
    fun map(httpStatus: Int, body: ApiErrorBody?): AppError {
        val code = body?.code
        return when (code) {
            "KIOSK_TOKEN_REQUIRED", "KIOSK_TOKEN_EXPIRED", "KIOSK_TOKEN_INVALID",
            "KIOSK_TERMINAL_REVOKED", "KIOSK_TERMINAL_TYPE_INVALID" -> AppError.TerminalRevoked
            "KIOSK_TERMINAL_DISABLED" -> AppError.TerminalDisabled
            "KIOSK_LOGIN_NOT_ALLOWED", "FORBIDDEN" -> AppError.NotAllowed
            "INSUFFICIENT_STOCK", "PRODUCT_NOT_AVAILABLE" -> AppError.OutOfStock(
                skuId = body?.data?.get("sku_id")?.jsonPrimitive?.contentOrNull,
                productId = body?.data?.get("product_id")?.jsonPrimitive?.contentOrNull,
            )
            "OUTSIDE_OPERATING_HOURS" -> AppError.OutsideHours
            "DINE_IN_NOT_ALLOWED", "TAKEAWAY_NOT_ALLOWED", "TABLE_REQUIRED" -> AppError.OrderTypeUnavailable
            "PROMPTPAY_NOT_CONFIGURED", "PAYMENT_NOT_CONFIGURED" -> AppError.PromptPayUnavailable
            "PAYMENT_METHOD_DISABLED" -> AppError.PaymentMethodDisabled
            // featureGuard's code; the only feature the kiosk API checks is enable_kiosk.
            "FEATURE_NOT_AVAILABLE" -> AppError.KioskNotEnabled
            "ORDER_ALREADY_PAID" -> AppError.AlreadyPaid
            "ORDER_ALREADY_SENT_TO_KITCHEN", "ORDER_PAY_AT_COUNTER" -> AppError.OrderInKitchen
            "NO_KIOSK_TERMINAL_AVAILABLE" -> AppError.NoKioskTerminal
            "INVALID_PIN" -> AppError.InvalidPin
            "WEAK_PIN" -> AppError.WeakPin
            "PIN_ATTEMPTS_EXCEEDED" -> AppError.TooManyPinAttempts
            else -> when (httpStatus) {
                401 -> AppError.InvalidCredentials
                else -> AppError.Server(code, body?.message)
            }
        }
    }
}

/**
 * Runs a Retrofit call and maps every failure to an [AppError]. A `success:false` body that
 * still came back 2xx is treated like an error response.
 */
suspend fun <T> apiCall(json: Json, block: suspend () -> ApiEnvelope<T>): AppResult<T> = try {
    val envelope = block()
    val data = envelope.data
    when {
        !envelope.success -> AppResult.Failure(ApiErrorMapper.map(200, ApiErrorBody(envelope.code, envelope.message)))
        data == null -> AppResult.Failure(AppError.Server(envelope.code, "Empty response"))
        else -> AppResult.Success(data)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(ApiErrorMapper.map(e.code(), parseErrorBody(json, e)))
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: Exception) {
    AppResult.Failure(AppError.Server(null, e.message))
}

/** Same as [apiCall] for endpoints whose `data` may legitimately be null (e.g. member not found). */
suspend fun <T> apiCallNullable(json: Json, block: suspend () -> ApiEnvelope<T>): AppResult<T?> = try {
    val envelope = block()
    if (envelope.success) AppResult.Success(envelope.data)
    else AppResult.Failure(ApiErrorMapper.map(200, ApiErrorBody(envelope.code, envelope.message)))
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(ApiErrorMapper.map(e.code(), parseErrorBody(json, e)))
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: Exception) {
    AppResult.Failure(AppError.Server(null, e.message))
}

private fun parseErrorBody(json: Json, e: HttpException): ApiErrorBody? = try {
    e.response()?.errorBody()?.string()?.let { json.decodeFromString(ApiErrorBody.serializer(), it) }
} catch (_: Exception) {
    null
}
