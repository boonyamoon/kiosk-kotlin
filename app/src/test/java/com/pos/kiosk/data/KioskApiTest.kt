package com.pos.kiosk.data

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.storage.KioskCredentialStore
import com.pos.kiosk.data.remote.AuthApi
import com.pos.kiosk.data.remote.KioskApi
import com.pos.kiosk.data.repository.AuthRepositoryImpl
import com.pos.kiosk.data.repository.MemberRepositoryImpl
import com.pos.kiosk.domain.model.StaffSession
import io.mockk.mockk
import com.pos.kiosk.data.repository.OrderRepositoryImpl
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.TestData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KioskApiTest {
    private val server = MockWebServer()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
    private lateinit var api: KioskApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KioskApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private val request = PlaceOrderRequest(OrderType.DINE_IN, PaymentMethod.CASH, null, listOf(TestData.cartItem()))

    @Test
    fun `place order sends the idempotency key and maps the response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"success":true,"data":{"order_id":"o-1","order_number":"KS-1","queue_number":"Q007","total_amount":48.15,
                   "payment_method":"CASH","kitchen_dispatched":true,"idempotent_replay":false,"subtotal":45}}""",
            ),
        )

        val result = OrderRepositoryImpl(api, json).placeOrder("key-123", request)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/kiosk/orders", recorded.path)
        assertEquals("key-123", recorded.getHeader("Idempotency-Key"))
        assertTrue(recorded.body.readUtf8().contains("\"payment_method\":\"CASH\""))
        val order = (result as AppResult.Success).data
        assertEquals("Q007", order.queueNumber)
        assertEquals(48.15, order.totalAmount, 0.0)
        assertTrue(order.kitchenDispatched)
    }

    @Test
    fun `insufficient stock maps to OutOfStock with the sku`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"success":false,"code":"INSUFFICIENT_STOCK","message":"Insufficient stock","data":{"sku_id":"s-m","on_hand":0,"required":1}}""",
            ),
        )

        val result = OrderRepositoryImpl(api, json).placeOrder("k", request)

        assertEquals(AppResult.Failure(AppError.OutOfStock("s-m")), result)
    }

    @Test
    fun `revoked kiosk token maps to TerminalRevoked`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"success":false,"code":"KIOSK_TERMINAL_REVOKED","message":"x"}"""))
        assertEquals(AppResult.Failure(AppError.TerminalRevoked), OrderRepositoryImpl(api, json).receipt("o-1"))
    }

    @Test
    fun `member lookup returns null for an unknown phone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":null}"""))
        val result = MemberRepositoryImpl(api, json).lookup("0812345678")
        assertNull((result as AppResult.Success).data)
    }

    @Test
    fun `member lookup maps a found member`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"customer_id":"c-1","display_name":"สมชาย ก.","available_points":120}}"""))
        val member = (MemberRepositoryImpl(api, json).lookup("0812345678") as AppResult.Success).data
        assertEquals("สมชาย ก.", member?.displayName)
        assertEquals(120, member?.availablePoints)
    }

    @Test
    fun `kiosk access tells no store apart from no kiosk permission`() = runTest {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val repository = AuthRepositoryImpl(retrofit.create(AuthApi::class.java), api, mockk<KioskCredentialStore>(relaxed = true), json)
        val staff = StaffSession("staff-token", "staff@meedee.co")
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"user_branch_assignments":[],"branches":[],"businesses":[],"platform_filtered":false}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"user_branch_assignments":[],"branches":[],"businesses":[],"platform_filtered":true}}"""))

        val noStore = (repository.kioskAccess(staff) as AppResult.Success).data
        val noPermission = (repository.kioskAccess(staff) as AppResult.Success).data

        assertTrue(noStore.branches.isEmpty() && !noStore.deniedByPermission)
        assertTrue(noPermission.branches.isEmpty() && noPermission.deniedByPermission)
        assertEquals("Bearer staff-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `branch image uses the branch photo, then the store logo`() = runTest {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val repository = AuthRepositoryImpl(retrofit.create(AuthApi::class.java), api, mockk<KioskCredentialStore>(relaxed = true), json)
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{
                  "user_branch_assignments":[{"branch_id":"b-1","is_active":true},{"branch_id":"b-2","is_active":true},{"branch_id":"b-3","is_active":true}],
                  "branches":[
                    {"id":"b-1","business_id":"biz-1","branch_name":"A","hero_image_url":"https://cdn/branch.jpg"},
                    {"id":"b-2","business_id":"biz-1","branch_name":"B","hero_image_url":""},
                    {"id":"b-3","business_id":"biz-2","branch_name":"C"}],
                  "businesses":[{"id":"biz-1","business_name":"MeeD Cafe","logo_url":"https://cdn/logo.png"},{"id":"biz-2","business_name":"Salon"}],
                  "platform_filtered":false}}""",
            ),
        )

        val branches = (repository.kioskAccess(StaffSession("t", "e")) as AppResult.Success).data.branches.associateBy { it.id }

        assertEquals("https://cdn/branch.jpg", branches.getValue("b-1").imageUrl)
        assertEquals("https://cdn/logo.png", branches.getValue("b-2").imageUrl)
        assertEquals(null, branches.getValue("b-3").imageUrl)
    }

    @Test
    fun `a dropped connection maps to Network`() = runTest {
        server.shutdown()
        assertEquals(AppResult.Failure(AppError.Network), OrderRepositoryImpl(api, json).receipt("o-1"))
    }
}
