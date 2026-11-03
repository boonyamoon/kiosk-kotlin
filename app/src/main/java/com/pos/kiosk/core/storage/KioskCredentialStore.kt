package com.pos.kiosk.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class KioskCredentials(
    val token: String,
    val tokenExpiresAtMillis: Long,
    val tokenIssuedAtMillis: Long,
    val terminalId: String,
    val terminalCode: String,
    val terminalName: String,
    val businessId: String,
    val branchId: String,
)

private val Context.kioskDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_credentials")

/**
 * The only persisted secret is the kiosk token (encrypted with [TokenCipher]); staff tokens are
 * never written to disk. [cachedToken] lets the OkHttp interceptor read the token without I/O.
 *
 * This class is the only writer of the store, so after the first [load] the decrypted credentials
 * are served from memory: no DataStore read or Keystore decryption on every token check. All disk
 * and Keystore work runs on [Dispatchers.IO], never on the caller's (often main) thread.
 */
@Singleton
class KioskCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cipher: TokenCipher,
) {
    /** null = not read from disk yet; a holder with null credentials = read, not paired. */
    private class Snapshot(val credentials: KioskCredentials?)

    @Volatile
    private var snapshot: Snapshot? = null
    private val loadLock = Mutex()

    val cachedToken: String?
        get() = snapshot?.credentials?.token

    suspend fun load(): KioskCredentials? {
        snapshot?.let { return it.credentials }
        return loadLock.withLock {
            snapshot?.let { return@withLock it.credentials }
            withContext(Dispatchers.IO) { readFromDisk() }.also { snapshot = Snapshot(it) }
        }
    }

    private suspend fun readFromDisk(): KioskCredentials? {
        val prefs = context.kioskDataStore.data.first()
        val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return null
        return KioskCredentials(
            token = token,
            tokenExpiresAtMillis = prefs[EXPIRES_AT] ?: 0L,
            tokenIssuedAtMillis = prefs[ISSUED_AT] ?: 0L,
            terminalId = prefs[TERMINAL_ID].orEmpty(),
            terminalCode = prefs[TERMINAL_CODE].orEmpty(),
            terminalName = prefs[TERMINAL_NAME].orEmpty(),
            businessId = prefs[BUSINESS_ID].orEmpty(),
            branchId = prefs[BRANCH_ID].orEmpty(),
        )
    }

    suspend fun save(credentials: KioskCredentials) {
        withContext(Dispatchers.IO) {
            val encrypted = cipher.encrypt(credentials.token)
            context.kioskDataStore.edit { prefs ->
                prefs[TOKEN] = encrypted
                prefs[EXPIRES_AT] = credentials.tokenExpiresAtMillis
                prefs[ISSUED_AT] = credentials.tokenIssuedAtMillis
                prefs[TERMINAL_ID] = credentials.terminalId
                prefs[TERMINAL_CODE] = credentials.terminalCode
                prefs[TERMINAL_NAME] = credentials.terminalName
                prefs[BUSINESS_ID] = credentials.businessId
                prefs[BRANCH_ID] = credentials.branchId
            }
        }
        snapshot = Snapshot(credentials)
    }

    /** Keeps the device install id: re-pairing the same tablet should find its old terminal. */
    suspend fun clear() {
        context.kioskDataStore.edit { prefs ->
            val deviceId = prefs[DEVICE_INSTALL_ID]
            prefs.clear()
            if (deviceId != null) prefs[DEVICE_INSTALL_ID] = deviceId
        }
        snapshot = Snapshot(null)
    }

    /** Stable per-install UUID sent as pos_terminals.device_install_id. */
    suspend fun deviceInstallId(): String {
        context.kioskDataStore.data.first()[DEVICE_INSTALL_ID]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.kioskDataStore.edit { prefs -> if (prefs[DEVICE_INSTALL_ID] == null) prefs[DEVICE_INSTALL_ID] = generated }
        return context.kioskDataStore.data.first()[DEVICE_INSTALL_ID] ?: generated
    }

    private companion object {
        val TOKEN = stringPreferencesKey("kiosk_token_enc")
        val EXPIRES_AT = longPreferencesKey("kiosk_token_expires_at")
        val ISSUED_AT = longPreferencesKey("kiosk_token_issued_at")
        val TERMINAL_ID = stringPreferencesKey("terminal_id")
        val TERMINAL_CODE = stringPreferencesKey("terminal_code")
        val TERMINAL_NAME = stringPreferencesKey("terminal_name")
        val BUSINESS_ID = stringPreferencesKey("business_id")
        val BRANCH_ID = stringPreferencesKey("branch_id")
        val DEVICE_INSTALL_ID = stringPreferencesKey("device_install_id")
    }
}
