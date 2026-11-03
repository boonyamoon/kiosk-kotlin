package com.pos.kiosk.core.network

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.storage.KioskCredentialStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** How the app reads the backend's "plan has no kiosk" and "payment method off" answers. */
class KioskEntitlementResponsesTest {
    private val server = MockWebServer()
    private val events = mockk<KioskSessionEvents>(relaxed = true)
    private val credentials = mockk<KioskCredentialStore> { every { cachedToken } returns "kiosk-token" }
    private val client = OkHttpClient.Builder().addInterceptor(KioskAuthInterceptor(credentials, events)).build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun call(status: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
        client.newCall(Request.Builder().url(server.url("/api/v1/kiosk/config")).build()).execute().close()
    }

    @Test
    fun `a plan without the kiosk shows out of service and never unpairs`() {
        call(403, """{"success":false,"code":"FEATURE_NOT_AVAILABLE","message":"Feature 'enable_kiosk' not enabled","data":{"feature_key":"enable_kiosk"}}""")

        verify { events.emit(KioskSessionEvent.NOT_ENABLED) }
        verify(exactly = 0) { events.emit(KioskSessionEvent.REVOKED) }
    }

    @Test
    fun `a revoked token still unpairs`() {
        call(401, """{"success":false,"code":"KIOSK_TERMINAL_REVOKED","message":"no longer paired"}""")

        verify { events.emit(KioskSessionEvent.REVOKED) }
    }

    @Test
    fun `the error codes map to their own messages`() {
        assertEquals(AppError.KioskNotEnabled, ApiErrorMapper.map(403, ApiErrorBody(code = "FEATURE_NOT_AVAILABLE")))
        assertEquals(AppError.PaymentMethodDisabled, ApiErrorMapper.map(403, ApiErrorBody(code = "PAYMENT_METHOD_DISABLED")))
    }
}
