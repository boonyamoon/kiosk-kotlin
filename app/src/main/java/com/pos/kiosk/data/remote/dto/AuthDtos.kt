package com.pos.kiosk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaffLoginRequestDto(val email: String, val password: String)

/** POST /api/v1/auth/login answers without the usual {success,data} envelope. */
@Serializable
data class StaffLoginResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class AccessSnapshotDto(
    @SerialName("user_branch_assignments") val assignments: List<BranchAssignmentDto> = emptyList(),
    val branches: List<SnapshotBranchDto> = emptyList(),
    val businesses: List<SnapshotBusinessDto> = emptyList(),
    /** True when branches existed but the kiosk permission (allow_kiosk_login) filtered them out. */
    @SerialName("platform_filtered") val platformFiltered: Boolean = false,
)

@Serializable
data class BranchAssignmentDto(
    @SerialName("branch_id") val branchId: String,
    @SerialName("is_active") val isActive: Boolean? = true,
)

@Serializable
data class SnapshotBranchDto(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("branch_name") val branchName: String? = null,
    @SerialName("hero_image_url") val heroImageUrl: String? = null,
)

@Serializable
data class SnapshotBusinessDto(
    val id: String,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
)

@Serializable
data class KioskTerminalDto(
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("terminal_code") val terminalCode: String,
    @SerialName("terminal_name") val terminalName: String,
    @SerialName("is_paired") val isPaired: Boolean,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class PairRequestDto(
    @SerialName("branch_id") val branchId: String,
    @SerialName("device_install_id") val deviceInstallId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("terminal_id") val terminalId: String? = null,
)

@Serializable
data class PairResponseDto(
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("terminal_code") val terminalCode: String,
    @SerialName("terminal_name") val terminalName: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("kiosk_token") val kioskToken: String,
    @SerialName("kiosk_token_expires_at") val kioskTokenExpiresAt: String,
)

@Serializable
data class TokenRefreshDto(
    @SerialName("kiosk_token") val kioskToken: String,
    @SerialName("kiosk_token_expires_at") val kioskTokenExpiresAt: String,
)
