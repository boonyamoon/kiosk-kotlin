package com.pos.kiosk.core.network

import com.pos.kiosk.core.storage.KioskCredentialStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the kiosk token to `/api/v1/kiosk/` calls that don't already carry an Authorization
 * header (setup calls pass the staff token explicitly), and turns revoked/disabled responses into
 * app-wide [KioskSessionEvents] so any screen can react.
 */
@Singleton
class KioskAuthInterceptor @Inject constructor(
    private val credentialStore: KioskCredentialStore,
    private val sessionEvents: KioskSessionEvents,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isKioskCall = request.url.encodedPath.contains("/api/v1/kiosk/")
        val token = credentialStore.cachedToken
        val usesKioskToken = isKioskCall && request.header("Authorization") == null && token != null

        val response = chain.proceed(
            if (usesKioskToken) request.newBuilder().header("Authorization", "Bearer $token").build() else request,
        )

        if (usesKioskToken && (response.code == 401 || response.code == 403)) {
            val body = response.peekBody(2048).string()
            when {
                body.contains("KIOSK_TERMINAL_DISABLED") -> sessionEvents.emit(KioskSessionEvent.DISABLED)
                // Checked before the KIOSK_ catch-all: a lapsed plan must never unpair the kiosk.
                body.contains("\"FEATURE_NOT_AVAILABLE\"") -> sessionEvents.emit(KioskSessionEvent.NOT_ENABLED)
                body.contains("\"KIOSK_") -> sessionEvents.emit(KioskSessionEvent.REVOKED)
            }
        }
        return response
    }
}
