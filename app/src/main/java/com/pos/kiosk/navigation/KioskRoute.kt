package com.pos.kiosk.navigation

import kotlinx.serialization.Serializable

sealed interface KioskRoute {
    @Serializable data object Attract : KioskRoute
    @Serializable data object Member : KioskRoute
    @Serializable data object Menu : KioskRoute
    @Serializable data object Cart : KioskRoute
    @Serializable data object Payment : KioskRoute
    @Serializable data class PromptPay(val orderId: String) : KioskRoute
    @Serializable data class Success(val orderId: String) : KioskRoute
    @Serializable data object Admin : KioskRoute
    @Serializable data object LockSetup : KioskRoute
}
