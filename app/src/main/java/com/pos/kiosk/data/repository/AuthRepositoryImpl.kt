package com.pos.kiosk.data.repository

import com.pos.kiosk.BuildConfig
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.map
import com.pos.kiosk.core.network.apiCall
import com.pos.kiosk.core.storage.KioskCredentialStore
import com.pos.kiosk.core.storage.KioskCredentials
import com.pos.kiosk.data.remote.AuthApi
import com.pos.kiosk.data.remote.KioskApi
import com.pos.kiosk.data.remote.dto.AdminUnlockRequestDto
import com.pos.kiosk.data.remote.dto.PairRequestDto
import com.pos.kiosk.data.remote.dto.StaffLoginRequestDto
import com.pos.kiosk.data.remote.parseIsoMillis
import com.pos.kiosk.domain.model.KioskAccess
import com.pos.kiosk.domain.model.KioskBranch
import com.pos.kiosk.domain.model.KioskTerminalOption
import com.pos.kiosk.domain.model.PairedTerminal
import com.pos.kiosk.domain.model.StaffSession
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.TokenLifetime
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val kioskApi: KioskApi,
    private val credentialStore: KioskCredentialStore,
    private val json: Json,
) : AuthRepository {

    override suspend fun staffLogin(email: String, password: String): AppResult<StaffSession> = try {
        val response = authApi.login(StaffLoginRequestDto(email.trim(), password))
        AppResult.Success(StaffSession(accessToken = response.accessToken, email = email.trim()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AppResult.Failure(if (e.code() == 401 || e.code() == 400) AppError.InvalidCredentials else AppError.Server(null, e.message()))
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network)
    }

    override suspend fun kioskAccess(staff: StaffSession): AppResult<KioskAccess> =
        apiCall(json) { authApi.accessSnapshot(staff.bearer()) }.map { snapshot ->
            val activeBranchIds = snapshot.assignments.filter { it.isActive != false }.map { it.branchId.lowercase() }.toSet()
            val businesses = snapshot.businesses.associateBy { it.id }
            val branches = snapshot.branches
                .filter { it.id.lowercase() in activeBranchIds }
                .map {
                    val business = businesses[it.businessId]
                    KioskBranch(
                        id = it.id,
                        name = it.branchName ?: it.id,
                        businessName = business?.businessName,
                        imageUrl = it.heroImageUrl?.takeIf(String::isNotBlank) ?: business?.logoUrl?.takeIf(String::isNotBlank),
                    )
                }
                .sortedWith(compareBy({ it.businessName.orEmpty() }, { it.name }))
            KioskAccess(branches = branches, deniedByPermission = snapshot.platformFiltered)
        }

    override suspend fun kioskTerminals(staff: StaffSession, branchId: String): AppResult<List<KioskTerminalOption>> =
        apiCall(json) { authApi.kioskTerminals(staff.bearer(), branchId) }.map { terminals ->
            terminals.map {
                KioskTerminalOption(id = it.terminalId, code = it.terminalCode, name = it.terminalName, isPaired = it.isPaired, pairedDeviceName = it.deviceName)
            }
        }

    override suspend fun pair(staff: StaffSession, branchId: String, terminalId: String?, deviceName: String): AppResult<PairedTerminal> {
        val request = PairRequestDto(
            branchId = branchId,
            deviceInstallId = credentialStore.deviceInstallId(),
            deviceName = deviceName.trim(),
            appVersion = BuildConfig.VERSION_NAME,
            terminalId = terminalId,
        )
        val result = apiCall(json) { authApi.pair(staff.bearer(), request) }
        if (result !is AppResult.Success) return result as AppResult.Failure
        val paired = result.data
        credentialStore.save(
            KioskCredentials(
                token = paired.kioskToken,
                tokenExpiresAtMillis = parseIsoMillis(paired.kioskTokenExpiresAt),
                tokenIssuedAtMillis = System.currentTimeMillis(),
                terminalId = paired.terminalId,
                terminalCode = paired.terminalCode,
                terminalName = paired.terminalName,
                businessId = paired.businessId,
                branchId = paired.branchId,
            ),
        )
        return AppResult.Success(
            PairedTerminal(paired.terminalId, paired.terminalCode, paired.terminalName, paired.businessId, paired.branchId),
        )
    }

    override suspend fun pairedTerminal(): PairedTerminal? = credentialStore.load()?.let {
        PairedTerminal(it.terminalId, it.terminalCode, it.terminalName, it.businessId, it.branchId)
    }

    override suspend fun tokenLifetime(): TokenLifetime? =
        credentialStore.load()?.let { TokenLifetime(it.tokenIssuedAtMillis, it.tokenExpiresAtMillis) }

    override suspend fun refreshToken(): AppResult<Unit> {
        val current = credentialStore.load() ?: return AppResult.Failure(AppError.TerminalRevoked)
        return apiCall(json) { kioskApi.refreshToken() }.map { refreshed ->
            credentialStore.save(
                current.copy(
                    token = refreshed.kioskToken,
                    tokenExpiresAtMillis = parseIsoMillis(refreshed.kioskTokenExpiresAt),
                    tokenIssuedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun forgetPairing() = credentialStore.clear()

    override suspend fun unlockAdmin(pin: String): AppResult<String> =
        apiCall(json) { kioskApi.unlockAdmin(AdminUnlockRequestDto(pin)) }.map { it.staffName }

    private fun StaffSession.bearer() = "Bearer $accessToken"
}
