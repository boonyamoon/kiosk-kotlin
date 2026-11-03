package com.pos.kiosk.domain.model

/** Lives only in the setup ViewModel's memory — never persisted. */
data class StaffSession(val accessToken: String, val email: String)

data class KioskBranch(
    val id: String,
    val name: String,
    val businessName: String?,
    /** Branch photo, else the store logo; null shows the MeeD icon. */
    val imageUrl: String? = null,
)

/**
 * Branches this staff account may set up a kiosk for. When [branches] is empty, [deniedByPermission]
 * tells "has a store but no kiosk permission" apart from "no store at all".
 */
data class KioskAccess(val branches: List<KioskBranch>, val deniedByPermission: Boolean)

data class KioskTerminalOption(
    val id: String,
    val code: String,
    val name: String,
    val isPaired: Boolean,
    val pairedDeviceName: String?,
)

data class PairedTerminal(
    val terminalId: String,
    val terminalCode: String,
    val terminalName: String,
    val businessId: String,
    val branchId: String,
)
