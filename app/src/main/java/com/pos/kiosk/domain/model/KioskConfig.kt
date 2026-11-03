package com.pos.kiosk.domain.model

data class KioskConfig(
    val terminalId: String,
    val terminalCode: String,
    val terminalName: String,
    val branch: BranchInfo,
    val allowDineIn: Boolean,
    val allowTakeaway: Boolean,
    val isOpen: Boolean,
    val closedReason: String?,
    val taxRate: Double,
    val taxIncluded: Boolean,
    val serviceChargeRate: Double,
    val cashEnabled: Boolean,
    val promptPayEnabled: Boolean,
    val receiptSettings: ReceiptSettings?,
    val receiptPrinter: ReceiptPrinterConfig?,
    val display: KioskDisplay = KioskDisplay(),
)

/** Look & feel from pos-web /kiosk-settings: first-screen background, idle screensaver, menu banners. */
data class KioskDisplay(
    val backgroundImageUrl: String? = null,
    val screensaver: Screensaver = Screensaver(),
    val banners: Banners = Banners(),
) {
    data class Screensaver(
        val enabled: Boolean = false,
        val idleSeconds: Int = 30,
        val slideSeconds: Int = 8,
        val images: List<DisplayImage> = emptyList(),
    ) {
        /** Worth starting only when switched on and there is something to show. */
        val active: Boolean get() = enabled && images.isNotEmpty()
    }

    data class Banners(val slideSeconds: Int = 5, val images: List<DisplayImage> = emptyList())

    /** Every image the kiosk may need while offline, for prefetching. */
    val allImageUrls: List<String>
        get() = listOfNotNull(backgroundImageUrl) + screensaver.images.map { it.url } + banners.images.map { it.url }
}

/** [id] is stable across reorders, so it keys pager pages and crossfades. */
data class DisplayImage(val id: String, val url: String)

data class BranchInfo(
    val businessId: String,
    val branchId: String,
    val branchName: String,
    val businessName: String,
    val logoUrl: String?,
    val themeColor: String,
    val welcomeMessage: String,
    val addressLine1: String?,
    val addressLine2: String?,
    val taxId: String?,
    val branchCode: String? = null,
    val phone: String? = null,
)

data class ReceiptSettings(
    val header: String,
    val showQueue: Boolean,
    val showTax: Boolean,
    val showMenuOptions: Boolean,
    val hideZeroPriceItems: Boolean,
    val footerLine1: String,
    val footerLine2: String,
)

data class ReceiptPrinterConfig(
    val name: String,
    val host: String,
    val port: Int,
    val paperSize: String,
    val copyCount: Int,
) {
    /** Printable width of common thermal heads at 203 dpi. */
    val paperWidthDots: Int get() = if (paperSize.startsWith("58")) 384 else 576
}
