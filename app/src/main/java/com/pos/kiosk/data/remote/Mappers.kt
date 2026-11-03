package com.pos.kiosk.data.remote

import com.pos.kiosk.data.remote.dto.CategoryDto
import com.pos.kiosk.data.remote.dto.KioskConfigDto
import com.pos.kiosk.data.remote.dto.MemberDto
import com.pos.kiosk.data.remote.dto.OrderItemDto
import com.pos.kiosk.data.remote.dto.PlaceOrderRequestDto
import com.pos.kiosk.data.remote.dto.PlacedOrderDto
import com.pos.kiosk.data.remote.dto.ProductDto
import com.pos.kiosk.data.remote.dto.PromptPayDto
import com.pos.kiosk.data.remote.dto.ReceiptDto
import com.pos.kiosk.data.remote.dto.SelectedOptionDto
import com.pos.kiosk.data.remote.dto.SkuDto
import com.pos.kiosk.domain.model.BranchInfo
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.OptionChoice
import com.pos.kiosk.domain.model.OptionGroup
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.PlaceOrderRequest
import com.pos.kiosk.domain.model.PlacedOrder
import com.pos.kiosk.domain.model.Product
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.PromptPayCharge
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.model.ReceiptItem
import com.pos.kiosk.domain.model.ReceiptPrinterConfig
import com.pos.kiosk.domain.model.ReceiptSettings
import com.pos.kiosk.domain.model.DisplayImage
import com.pos.kiosk.domain.model.KioskDisplay
import com.pos.kiosk.domain.model.Sku
import java.time.Instant

fun parseIsoMillis(value: String?): Long = try {
    if (value.isNullOrBlank()) 0L else Instant.parse(value).toEpochMilli()
} catch (_: Exception) {
    0L
}

// Same bounds as the backend validation; a bad value never makes the kiosk spin or stall.
private val IDLE_SECONDS = 10..600
private val SLIDE_SECONDS = 3..60
private val BANNER_SECONDS = 3..30

fun KioskConfigDto.DisplayDto.toDomain() = KioskDisplay(
    backgroundImageUrl = backgroundImageUrl?.takeIf { it.isNotBlank() },
    screensaver = KioskDisplay.Screensaver(
        enabled = screensaver.enabled,
        idleSeconds = screensaver.idleSeconds.coerceIn(IDLE_SECONDS),
        slideSeconds = screensaver.slideSeconds.coerceIn(SLIDE_SECONDS),
        images = screensaver.images.toDomain(),
    ),
    banners = KioskDisplay.Banners(
        slideSeconds = banners.slideSeconds.coerceIn(BANNER_SECONDS),
        images = banners.images.toDomain(),
    ),
)

private fun List<KioskConfigDto.DisplayImageDto>.toDomain() =
    filter { it.url.isNotBlank() }.distinctBy { it.id }.map { DisplayImage(id = it.id, url = it.url) }

fun KioskConfigDto.toDomain() = KioskConfig(
    terminalId = terminal.terminalId,
    terminalCode = terminal.terminalCode,
    terminalName = terminal.terminalName,
    branch = BranchInfo(
        businessId = branch.businessId,
        branchId = branch.branchId,
        branchName = branch.branchName,
        businessName = branch.businessName,
        logoUrl = branch.logoUrl,
        themeColor = branch.themeColor,
        welcomeMessage = branch.welcomeMessage,
        addressLine1 = branch.addressLine1,
        addressLine2 = branch.addressLine2,
        taxId = branch.taxId,
        branchCode = branch.branchCode?.takeIf { it.isNotBlank() },
        phone = branch.phone?.takeIf { it.isNotBlank() },
    ),
    allowDineIn = ordering.allowDineIn,
    allowTakeaway = ordering.allowTakeaway,
    isOpen = ordering.isOpen,
    closedReason = ordering.closedReason,
    taxRate = tax.taxRate,
    taxIncluded = tax.taxIncluded,
    serviceChargeRate = tax.serviceChargeRate,
    cashEnabled = paymentMethods.cash,
    promptPayEnabled = paymentMethods.promptpay,
    receiptSettings = receiptSettings?.let {
        ReceiptSettings(
            header = it.receiptHeader,
            showQueue = it.showQueue,
            showTax = it.showTax,
            showMenuOptions = it.showMenuOptions,
            hideZeroPriceItems = it.hideZeroPriceItems,
            footerLine1 = it.footerLine1,
            footerLine2 = it.footerLine2,
        )
    },
    receiptPrinter = receiptPrinter?.let {
        ReceiptPrinterConfig(name = it.name, host = it.ipAddress, port = it.port, paperSize = it.paperSize, copyCount = it.copyCount.coerceIn(1, 3))
    },
    display = display.toDomain(),
)

fun CategoryDto.toDomain() = Category(id = id, name = displayName ?: categoryName)

private fun SkuDto.toDomain() = Sku(id = id, name = skuName, price = defaultPrice, isDefault = isDefault)

fun ProductDto.toProduct() = Product(
    id = id,
    name = productName,
    description = description,
    categoryId = categoryId,
    imageUrl = imageUrl,
    isAvailable = isAvailable,
    skus = skus.filter { it.isActive }.map { it.toDomain() },
    hasOptionGroups = optionGroupAssignments.isNotEmpty(),
    serviceCharge = serviceCharge == true,
)

fun ProductDto.toDetail() = ProductDetail(
    id = id,
    name = productName,
    description = description,
    imageUrl = imageUrl,
    isAvailable = isAvailable,
    serviceCharge = serviceCharge == true,
    skus = skus.filter { it.isActive }.map { it.toDomain() },
    optionGroups = optionGroups.map { group ->
        OptionGroup(
            id = group.id,
            name = group.groupName,
            nameEn = group.groupNameEn,
            isRequired = group.isRequired,
            minSelections = group.minSelections ?: 0,
            maxSelections = group.maxSelections?.takeIf { it > 0 },
            options = group.options.filter { it.isActive }.map { option ->
                OptionChoice(
                    id = option.id,
                    name = option.optionName,
                    nameEn = option.optionNameEn,
                    priceAdjustment = option.priceAdjustment,
                    isDefault = option.isDefault,
                )
            },
        )
    }.filter { it.options.isNotEmpty() },
)

fun MemberDto.toDomain() = Member(customerId = customerId, displayName = displayName, availablePoints = availablePoints)

fun PlaceOrderRequest.toDto() = PlaceOrderRequestDto(
    orderType = orderType.name,
    paymentMethod = paymentMethod.name,
    customerId = customerId,
    items = items.map { item ->
        OrderItemDto(
            productId = item.productId,
            skuId = item.skuId,
            productName = item.productName,
            skuName = item.skuName,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            note = item.note?.takeIf { it.isNotBlank() },
            selectedOptions = item.options.map {
                SelectedOptionDto(optionGroupId = it.groupId, optionId = it.optionId, optionName = it.name, priceAdjustment = it.priceAdjustment)
            },
        )
    },
)

fun PlacedOrderDto.toDomain() = PlacedOrder(
    orderId = orderId,
    orderNumber = orderNumber,
    queueNumber = queueNumber,
    totalAmount = totalAmount,
    paymentMethod = if (paymentMethod == "PROMPTPAY") PaymentMethod.PROMPTPAY else PaymentMethod.CASH,
    kitchenDispatched = kitchenDispatched,
)

fun PromptPayDto.toDomain() = PromptPayCharge(
    orderId = orderId,
    amount = amount,
    qrImageBase64 = qrCode.imageBase64.substringAfter("base64,"),
    expiresAtMillis = parseIsoMillis(qrCode.expiresAt),
)

fun ReceiptDto.toDomain() = Receipt(
    orderId = orderId,
    orderNumber = orderNumber,
    queueNumber = queueNumber,
    orderType = if (orderType == "TAKE_AWAY") OrderType.TAKE_AWAY else OrderType.DINE_IN,
    isPaid = paymentStatus == "PAID",
    paymentMethod = when (paymentMethod) {
        "PROMPTPAY" -> PaymentMethod.PROMPTPAY
        "CASH" -> PaymentMethod.CASH
        else -> null
    },
    createdAtMillis = parseIsoMillis(createdAt),
    memberName = customer?.displayName,
    items = items.map { item ->
        ReceiptItem(
            name = item.productName,
            skuName = item.skuName,
            quantity = item.quantity,
            lineTotal = item.lineTotal,
            note = item.note,
            options = item.options.map { if (it.quantity > 1) "${it.optionName} x${it.quantity}" else it.optionName },
        )
    },
    subtotal = subtotal,
    discount = discountAmount,
    serviceChargeRate = serviceChargeRate,
    serviceCharge = serviceChargeAmount,
    taxRate = taxRate,
    taxIncluded = taxIncluded,
    taxAmount = taxAmount,
    total = totalAmount,
    receiptNumber = receiptNumber?.takeIf { it.isNotBlank() },
    rounding = roundingAmount,
    paidAmount = paidAmount,
)
