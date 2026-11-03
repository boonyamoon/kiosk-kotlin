package com.pos.kiosk.data.remote

import com.pos.kiosk.core.network.ApiEnvelope
import com.pos.kiosk.data.remote.dto.AccessSnapshotDto
import com.pos.kiosk.data.remote.dto.AdminUnlockDto
import com.pos.kiosk.data.remote.dto.AdminUnlockRequestDto
import com.pos.kiosk.data.remote.dto.CategoryDto
import com.pos.kiosk.data.remote.dto.KioskConfigDto
import com.pos.kiosk.data.remote.dto.KioskTerminalDto
import com.pos.kiosk.data.remote.dto.MemberDto
import com.pos.kiosk.data.remote.dto.MemberLookupRequestDto
import com.pos.kiosk.data.remote.dto.MenuAvailabilityDto
import com.pos.kiosk.data.remote.dto.PairRequestDto
import com.pos.kiosk.data.remote.dto.PairResponseDto
import com.pos.kiosk.data.remote.dto.PaymentStatusDto
import com.pos.kiosk.data.remote.dto.PlaceOrderRequestDto
import com.pos.kiosk.data.remote.dto.PlacedOrderDto
import com.pos.kiosk.data.remote.dto.ProductDto
import com.pos.kiosk.data.remote.dto.ProductPageDto
import com.pos.kiosk.data.remote.dto.PromptPayDto
import com.pos.kiosk.data.remote.dto.ReceiptDto
import com.pos.kiosk.data.remote.dto.StaffLoginRequestDto
import com.pos.kiosk.data.remote.dto.StaffLoginResponseDto
import com.pos.kiosk.data.remote.dto.TokenRefreshDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Staff-authenticated setup endpoints — the staff token is passed explicitly, never stored. */
interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: StaffLoginRequestDto): StaffLoginResponseDto

    @GET("api/v1/branches-access/snapshot")
    suspend fun accessSnapshot(
        @Header("Authorization") bearer: String,
        @Query("platform") platform: String = "kiosk",
    ): ApiEnvelope<AccessSnapshotDto>

    @GET("api/v1/kiosk/pairing/terminals")
    suspend fun kioskTerminals(
        @Header("Authorization") bearer: String,
        @Query("branch_id") branchId: String,
    ): ApiEnvelope<List<KioskTerminalDto>>

    @POST("api/v1/kiosk/pair")
    suspend fun pair(@Header("Authorization") bearer: String, @Body body: PairRequestDto): ApiEnvelope<PairResponseDto>
}

/** Kiosk-token endpoints; KioskAuthInterceptor adds the token. */
interface KioskApi {
    @POST("api/v1/kiosk/token/refresh")
    suspend fun refreshToken(): ApiEnvelope<TokenRefreshDto>

    @GET("api/v1/kiosk/config")
    suspend fun config(): ApiEnvelope<KioskConfigDto>

    @GET("api/v1/kiosk/menu/categories")
    suspend fun categories(): ApiEnvelope<List<CategoryDto>>

    @GET("api/v1/kiosk/menu/products")
    suspend fun products(@Query("page") page: Int, @Query("limit") limit: Int = 100): ProductPageDto

    /** Polled every ~20 s on the ordering screens, so sold-out items show between full menu loads. */
    @GET("api/v1/kiosk/menu/availability")
    suspend fun menuAvailability(): ApiEnvelope<MenuAvailabilityDto>

    @GET("api/v1/kiosk/menu/products/{productId}")
    suspend fun productDetail(@Path("productId") productId: String): ApiEnvelope<ProductDto>

    /** Hidden staff menu: staff PIN, failed attempts rate-limited per terminal. */
    @POST("api/v1/kiosk/admin/unlock")
    suspend fun unlockAdmin(@Body body: AdminUnlockRequestDto): ApiEnvelope<AdminUnlockDto>

    @POST("api/v1/kiosk/customers/lookup")
    suspend fun lookupMember(@Body body: MemberLookupRequestDto): ApiEnvelope<MemberDto>

    @POST("api/v1/kiosk/orders")
    suspend fun placeOrder(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: PlaceOrderRequestDto,
    ): ApiEnvelope<PlacedOrderDto>

    @GET("api/v1/kiosk/orders/{orderId}")
    suspend fun receipt(@Path("orderId") orderId: String): ApiEnvelope<ReceiptDto>

    @POST("api/v1/kiosk/orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: String): ApiEnvelope<JsonObject>

    @POST("api/v1/kiosk/orders/{orderId}/pay-at-counter")
    suspend fun payAtCounter(@Path("orderId") orderId: String): ApiEnvelope<JsonObject>

    @POST("api/v1/kiosk/orders/{orderId}/payment/qr-promptpay")
    suspend fun createPromptPay(@Path("orderId") orderId: String): ApiEnvelope<PromptPayDto>

    @GET("api/v1/kiosk/orders/{orderId}/payment/status")
    suspend fun paymentStatus(@Path("orderId") orderId: String): ApiEnvelope<PaymentStatusDto>

    @POST("api/v1/kiosk/orders/{orderId}/payment/cancel")
    suspend fun cancelPayment(@Path("orderId") orderId: String): ApiEnvelope<JsonObject>
}
