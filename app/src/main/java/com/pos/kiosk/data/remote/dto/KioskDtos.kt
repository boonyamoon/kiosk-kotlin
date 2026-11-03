package com.pos.kiosk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KioskConfigDto(
    val terminal: TerminalDto,
    val branch: BranchDto,
    val ordering: OrderingDto,
    val tax: TaxDto,
    @SerialName("payment_methods") val paymentMethods: PaymentMethodsDto,
    @SerialName("receipt_settings") val receiptSettings: ReceiptSettingsDto? = null,
    @SerialName("receipt_printer") val receiptPrinter: ReceiptPrinterDto? = null,
    // Absent on backends older than the kiosk-settings feature: everything inside has defaults.
    val display: DisplayDto = DisplayDto(),
) {
    @Serializable
    data class TerminalDto(
        @SerialName("terminal_id") val terminalId: String,
        @SerialName("terminal_code") val terminalCode: String,
        @SerialName("terminal_name") val terminalName: String,
    )

    @Serializable
    data class BranchDto(
        @SerialName("business_id") val businessId: String,
        @SerialName("branch_id") val branchId: String,
        @SerialName("branch_name") val branchName: String = "",
        @SerialName("business_name") val businessName: String = "",
        // Kiosk branding (pos-web /kiosk-settings), separate from mobile order; the logo falls back to the business logo.
        @SerialName("logo_url") val logoUrl: String? = null,
        @SerialName("theme_color") val themeColor: String = "#FF5E00",
        @SerialName("welcome_message") val welcomeMessage: String = "",
        @SerialName("address_line1") val addressLine1: String? = null,
        @SerialName("address_line2") val addressLine2: String? = null,
        @SerialName("tax_id") val taxId: String? = null,
        @SerialName("branch_code") val branchCode: String? = null,
        val phone: String? = null,
    )

    @Serializable
    data class OrderingDto(
        @SerialName("allow_dine_in") val allowDineIn: Boolean = true,
        @SerialName("allow_takeaway") val allowTakeaway: Boolean = true,
        @SerialName("is_open") val isOpen: Boolean = true,
        @SerialName("closed_reason") val closedReason: String? = null,
    )

    @Serializable
    data class TaxDto(
        @SerialName("tax_rate") val taxRate: Double = 7.0,
        @SerialName("tax_included") val taxIncluded: Boolean = true,
        @SerialName("service_charge_rate") val serviceChargeRate: Double = 0.0,
    )

    @Serializable
    data class PaymentMethodsDto(val cash: Boolean = true, val promptpay: Boolean = false)

    @Serializable
    data class ReceiptSettingsDto(
        @SerialName("receipt_header") val receiptHeader: String = "",
        @SerialName("show_queue") val showQueue: Boolean = true,
        @SerialName("show_tax") val showTax: Boolean = true,
        @SerialName("show_menu_options") val showMenuOptions: Boolean = true,
        @SerialName("hide_zero_price_items") val hideZeroPriceItems: Boolean = false,
        @SerialName("footer_line1") val footerLine1: String = "",
        @SerialName("footer_line2") val footerLine2: String = "",
    )

    /** Look & feel set on pos-web /kiosk-settings. */
    @Serializable
    data class DisplayDto(
        @SerialName("background_image_url") val backgroundImageUrl: String? = null,
        val screensaver: ScreensaverDto = ScreensaverDto(),
        val banners: BannersDto = BannersDto(),
    )

    @Serializable
    data class ScreensaverDto(
        val enabled: Boolean = false,
        @SerialName("idle_seconds") val idleSeconds: Int = 30,
        @SerialName("slide_seconds") val slideSeconds: Int = 8,
        val images: List<DisplayImageDto> = emptyList(),
    )

    @Serializable
    data class BannersDto(
        @SerialName("slide_seconds") val slideSeconds: Int = 5,
        val images: List<DisplayImageDto> = emptyList(),
    )

    @Serializable
    data class DisplayImageDto(val id: String, val url: String)

    @Serializable
    data class ReceiptPrinterDto(
        val name: String = "",
        @SerialName("ip_address") val ipAddress: String,
        val port: Int = 9100,
        @SerialName("paper_size") val paperSize: String = "80mm",
        @SerialName("copy_count") val copyCount: Int = 1,
    )
}

@Serializable
data class CategoryDto(
    val id: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class ProductPageDto(
    val success: Boolean = true,
    val data: List<ProductDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
data class PaginationDto(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("has_next_page") val hasNextPage: Boolean,
)

@Serializable
data class ProductDto(
    val id: String,
    @SerialName("product_name") val productName: String,
    @SerialName("description_text") val description: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("is_hidden_from_menu") val isHiddenFromMenu: Boolean = false,
    val skus: List<SkuDto> = emptyList(),
    @SerialName("option_group_assignments") val optionGroupAssignments: List<OptionGroupAssignmentDto> = emptyList(),
    @SerialName("option_groups") val optionGroups: List<OptionGroupDto> = emptyList(),
    @SerialName("service_charge") val serviceCharge: Boolean? = null,
)

@Serializable
data class SkuDto(
    val id: String,
    @SerialName("sku_name") val skuName: String,
    @SerialName("default_price") val defaultPrice: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class OptionGroupAssignmentDto(@SerialName("option_group_id") val optionGroupId: String)

@Serializable
data class OptionGroupDto(
    val id: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("group_name_en") val groupNameEn: String? = null,
    @SerialName("is_required") val isRequired: Boolean = false,
    @SerialName("min_selections") val minSelections: Int? = 0,
    @SerialName("max_selections") val maxSelections: Int? = null,
    val options: List<OptionDto> = emptyList(),
)

@Serializable
data class OptionDto(
    val id: String,
    @SerialName("option_name") val optionName: String,
    @SerialName("option_name_en") val optionNameEn: String? = null,
    @SerialName("price_adjustment") val priceAdjustment: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class MemberLookupRequestDto(val phone: String)

@Serializable
data class AdminUnlockRequestDto(val pin: String)

@Serializable
data class AdminUnlockDto(
    @SerialName("staff_id") val staffId: String = "",
    @SerialName("staff_name") val staffName: String = "",
)

@Serializable
data class MemberDto(
    @SerialName("customer_id") val customerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("available_points") val availablePoints: Int = 0,
)

@Serializable
data class PlaceOrderRequestDto(
    @SerialName("order_type") val orderType: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("customer_id") val customerId: String? = null,
    val items: List<OrderItemDto>,
)

@Serializable
data class OrderItemDto(
    @SerialName("product_id") val productId: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("sku_name") val skuName: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
    val note: String? = null,
    @SerialName("selected_options") val selectedOptions: List<SelectedOptionDto> = emptyList(),
)

@Serializable
data class SelectedOptionDto(
    @SerialName("option_group_id") val optionGroupId: String,
    @SerialName("option_id") val optionId: String,
    @SerialName("option_name") val optionName: String,
    @SerialName("price_adjustment") val priceAdjustment: Double,
    val quantity: Int = 1,
)

@Serializable
data class PlacedOrderDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("queue_number") val queueNumber: String? = null,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("kitchen_dispatched") val kitchenDispatched: Boolean = false,
)

@Serializable
data class PromptPayDto(
    @SerialName("order_id") val orderId: String,
    val amount: Double,
    @SerialName("qr_code") val qrCode: QrCodeDto,
) {
    @Serializable
    data class QrCodeDto(
        @SerialName("image_base64") val imageBase64: String,
        @SerialName("expires_at") val expiresAt: String,
    )
}

@Serializable
data class PaymentStatusDto(
    @SerialName("order_status") val orderStatus: String,
    val paid: Boolean,
    val payment: PaymentDto? = null,
) {
    @Serializable
    data class PaymentDto(val status: String)
}

@Serializable
data class ReceiptDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("queue_number") val queueNumber: String? = null,
    @SerialName("order_type") val orderType: String = "DINE_IN",
    @SerialName("payment_status") val paymentStatus: String = "UNPAID",
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("created_at") val createdAt: String,
    val customer: ReceiptCustomerDto? = null,
    val items: List<ReceiptItemDto> = emptyList(),
    val subtotal: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("service_charge_rate") val serviceChargeRate: Double = 0.0,
    @SerialName("service_charge_amount") val serviceChargeAmount: Double = 0.0,
    @SerialName("tax_rate") val taxRate: Double = 0.0,
    @SerialName("tax_included") val taxIncluded: Boolean = true,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("rounding_amount") val roundingAmount: Double = 0.0,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
) {
    @Serializable
    data class ReceiptCustomerDto(@SerialName("display_name") val displayName: String)

    @Serializable
    data class ReceiptItemDto(
        @SerialName("product_name") val productName: String,
        @SerialName("sku_name") val skuName: String? = null,
        val quantity: Double,
        @SerialName("line_total") val lineTotal: Double,
        val note: String? = null,
        val options: List<ReceiptOptionDto> = emptyList(),
    )

    @Serializable
    data class ReceiptOptionDto(
        @SerialName("option_name") val optionName: String,
        val quantity: Int = 1,
    )
}

/** GET /kiosk/menu/availability: products that can't be ordered right now (sold out, switched off, hidden). */
@Serializable
data class MenuAvailabilityDto(
    @SerialName("unavailable_product_ids") val unavailableProductIds: List<String> = emptyList(),
)
