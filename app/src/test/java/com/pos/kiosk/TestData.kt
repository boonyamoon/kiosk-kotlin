package com.pos.kiosk

import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.OptionChoice
import com.pos.kiosk.domain.model.OptionGroup
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.SelectedOption
import com.pos.kiosk.domain.model.Sku

object TestData {
    val sweetness = OptionGroup(
        id = "g-sweet",
        name = "ความหวาน",
        nameEn = "Sweetness",
        isRequired = true,
        minSelections = 1,
        maxSelections = 1,
        options = listOf(
            OptionChoice("o-0", "ไม่หวาน", "No sugar", 0.0, isDefault = false),
            OptionChoice("o-50", "หวานน้อย", "Less sweet", 0.0, isDefault = true),
        ),
    )

    val toppings = OptionGroup(
        id = "g-top",
        name = "ท็อปปิ้ง",
        nameEn = "Toppings",
        isRequired = false,
        minSelections = 0,
        maxSelections = 2,
        options = listOf(
            OptionChoice("t-pearl", "ไข่มุก", "Pearl", 10.0, isDefault = false),
            OptionChoice("t-jelly", "เจลลี่", "Jelly", 10.0, isDefault = false),
            OptionChoice("t-pudding", "พุดดิ้ง", "Pudding", 15.0, isDefault = false),
        ),
    )

    val milkTea = ProductDetail(
        id = "p-milk-tea",
        name = "ชานม",
        description = null,
        imageUrl = null,
        isAvailable = true,
        skus = listOf(Sku("s-m", "M", 45.0, isDefault = true), Sku("s-l", "L", 55.0, isDefault = false)),
        optionGroups = listOf(sweetness, toppings),
    )

    fun cartItem(
        lineId: String = "line-1",
        skuId: String = "s-m",
        quantity: Int = 1,
        options: List<SelectedOption> = emptyList(),
        note: String? = null,
    ) = CartItem(
        lineId = lineId,
        productId = "p-milk-tea",
        productName = "ชานม",
        skuId = skuId,
        skuName = "M",
        unitPrice = 45.0,
        quantity = quantity,
        options = options,
        note = note,
        imageUrl = null,
    )
}
