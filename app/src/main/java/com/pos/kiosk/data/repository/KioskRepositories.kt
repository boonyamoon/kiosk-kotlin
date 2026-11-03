package com.pos.kiosk.data.repository

import android.os.SystemClock
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.map
import com.pos.kiosk.core.common.onSuccess
import com.pos.kiosk.core.network.apiCall
import com.pos.kiosk.core.network.apiCallNullable
import com.pos.kiosk.data.remote.KioskApi
import com.pos.kiosk.data.remote.dto.MemberLookupRequestDto
import com.pos.kiosk.data.remote.dto.ProductDto
import com.pos.kiosk.data.remote.toDetail
import com.pos.kiosk.data.remote.toDomain
import com.pos.kiosk.data.remote.toDto
import com.pos.kiosk.data.remote.toProduct
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.PaymentState
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.model.Product
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.PromptPayCharge
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MemberRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: KioskApi,
    private val json: Json,
) : ConfigRepository {
    private val _config = MutableStateFlow<KioskConfig?>(null)
    override val config: StateFlow<KioskConfig?> = _config.asStateFlow()

    override suspend fun refresh(): AppResult<KioskConfig> =
        apiCall(json) { api.config() }.map { it.toDomain() }.onSuccess { _config.value = it }
}

/** In-memory cache of the whole menu; refreshed when the attract screen sees it is stale. */
@Singleton
class MenuRepositoryImpl @Inject constructor(
    private val api: KioskApi,
    private val json: Json,
) : MenuRepository {
    // One lock per cache: categories and products can load in parallel, while two callers of the
    // same cache still share one request.
    private val categoriesLock = Mutex()
    private val productsLock = Mutex()
    private var categories: List<Category>? = null
    private var products: List<Product>? = null

    /** Monotonic time of the last product load (null = never loaded); read without the lock by [isStale]. */
    @Volatile
    private var loadedAtElapsed: Long? = null

    private val _unavailable = MutableStateFlow<Set<String>>(emptySet())
    override val unavailableProductIds: StateFlow<Set<String>> = _unavailable.asStateFlow()

    override suspend fun refreshAvailability(): AppResult<Set<String>> =
        apiCall(json) { api.menuAvailability() }
            .map { it.unavailableProductIds.toSet() }
            .onSuccess { _unavailable.value = it }

    override fun markUnavailable(productId: String) {
        _unavailable.update { it + productId }
    }

    // elapsedRealtime, not wall time: an NTP/clock change must not make the cache look fresh or stale.
    override fun isStale(): Boolean =
        loadedAtElapsed?.let { SystemClock.elapsedRealtime() - it > STALE_AFTER_MS } ?: true

    override suspend fun categories(forceRefresh: Boolean): AppResult<List<Category>> = categoriesLock.withLock {
        categories?.takeIf { !forceRefresh }?.let { return AppResult.Success(it) }
        apiCall(json) { api.categories() }.map { list -> list.map { it.toDomain() } }.onSuccess { categories = it }
    }

    override suspend fun products(forceRefresh: Boolean): AppResult<List<Product>> = productsLock.withLock {
        products?.takeIf { !forceRefresh }?.let { return AppResult.Success(it) }
        loadAllProducts().onSuccess { list ->
            products = list
            loadedAtElapsed = SystemClock.elapsedRealtime()
            _unavailable.value = list.filterNot { it.isAvailable }.mapTo(HashSet()) { it.id }
        }
    }

    override suspend fun productDetail(productId: String): AppResult<ProductDetail> =
        apiCall(json) { api.productDetail(productId) }.map { it.toDetail() }

    private suspend fun loadAllProducts(): AppResult<List<Product>> = try {
        val all = mutableListOf<ProductDto>()
        var page = 1
        do {
            val response = api.products(page = page)
            all += response.data
            val hasNext = response.pagination?.hasNextPage == true
            page++
        } while (hasNext && page <= MAX_PAGES)
        AppResult.Success(all.filter { !it.isHiddenFromMenu }.map { it.toProduct() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network)
    } catch (e: Exception) {
        AppResult.Failure(AppError.Server(null, e.message))
    }

    private companion object {
        const val STALE_AFTER_MS = 5 * 60 * 1000L
        const val MAX_PAGES = 20
    }
}

@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val api: KioskApi,
    private val json: Json,
) : MemberRepository {
    override suspend fun lookup(phone: String): AppResult<Member?> =
        apiCallNullable(json) { api.lookupMember(MemberLookupRequestDto(phone)) }.map { it?.toDomain() }
}

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val api: KioskApi,
    private val json: Json,
) : OrderRepository {
    override suspend fun placeOrder(idempotencyKey: String, request: PlaceOrderRequest): AppResult<PlacedOrder> =
        apiCall(json) { api.placeOrder(idempotencyKey, request.toDto()) }.map { it.toDomain() }

    override suspend fun receipt(orderId: String): AppResult<Receipt> =
        apiCall(json) { api.receipt(orderId) }.map { it.toDomain() }

    override suspend fun cancel(orderId: String): AppResult<Unit> =
        apiCall(json) { api.cancelOrder(orderId) }.map { }

    override suspend fun payAtCounter(orderId: String): AppResult<Unit> =
        apiCall(json) { api.payAtCounter(orderId) }.map { }
}

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val api: KioskApi,
    private val json: Json,
) : PaymentRepository {
    override suspend fun createPromptPay(orderId: String): AppResult<PromptPayCharge> =
        apiCall(json) { api.createPromptPay(orderId) }.map { it.toDomain() }

    override suspend fun status(orderId: String): AppResult<PaymentState> =
        apiCall(json) { api.paymentStatus(orderId) }.map { PaymentState(paid = it.paid, orderStatus = it.orderStatus, paymentStatus = it.payment?.status) }

    override suspend fun cancelPending(orderId: String): AppResult<Unit> =
        apiCall(json) { api.cancelPayment(orderId) }.map { }
}
