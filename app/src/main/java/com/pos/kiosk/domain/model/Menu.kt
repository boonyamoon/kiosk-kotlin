package com.pos.kiosk.domain.model

data class Category(val id: String, val name: String)

data class Sku(val id: String, val name: String, val price: Double, val isDefault: Boolean)

data class Product(
    val id: String,
    val name: String,
    val description: String?,
    val categoryId: String?,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val skus: List<Sku>,
    val hasOptionGroups: Boolean,
    /** Counted in the branch's service charge (products.service_charge); most products are not. */
    val serviceCharge: Boolean = false,
) {
    val defaultSku: Sku? get() = skus.firstOrNull { it.isDefault } ?: skus.firstOrNull()
    val startingPrice: Double get() = skus.minOfOrNull { it.price } ?: 0.0

    /** Needs the option sheet when there is anything to choose; otherwise one tap adds it. */
    val needsCustomization: Boolean get() = hasOptionGroups || skus.size > 1
}

data class ProductDetail(
    val id: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val skus: List<Sku>,
    val optionGroups: List<OptionGroup>,
    val serviceCharge: Boolean = false,
)

data class OptionGroup(
    val id: String,
    val name: String,
    val nameEn: String?,
    val isRequired: Boolean,
    val minSelections: Int,
    /** null = unlimited. */
    val maxSelections: Int?,
    val options: List<OptionChoice>,
) {
    val isSingleChoice: Boolean get() = maxSelections == 1
    /** Minimum picks, capped by what the group can offer so a mis-configured group never blocks ordering. */
    val effectiveMin: Int
        get() {
            val wanted = if (isRequired) maxOf(1, minSelections) else minSelections
            return wanted.coerceAtMost(options.size).coerceAtMost(maxSelections ?: Int.MAX_VALUE)
        }

    fun displayName(english: Boolean): String = if (english && !nameEn.isNullOrBlank()) nameEn else name
}

data class OptionChoice(
    val id: String,
    val name: String,
    val nameEn: String?,
    val priceAdjustment: Double,
    val isDefault: Boolean,
) {
    fun displayName(english: Boolean): String = if (english && !nameEn.isNullOrBlank()) nameEn else name
}
