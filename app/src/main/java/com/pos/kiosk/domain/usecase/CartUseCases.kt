package com.pos.kiosk.domain.usecase

import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.OptionGroup
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.SelectedOption
import com.pos.kiosk.domain.repository.OrderSessionRepository
import java.util.UUID
import javax.inject.Inject

/** Selection rules for option groups (required / min / max), shared by the option sheet and add-to-cart. */
class OptionSelectionRules @Inject constructor() {

    fun defaults(groups: List<OptionGroup>): Map<String, Set<String>> = groups.associate { group ->
        group.id to group.options.filter { it.isDefault }.map { it.id }.take(group.maxSelections ?: Int.MAX_VALUE).toSet()
    }

    fun toggle(group: OptionGroup, current: Set<String>, optionId: String): Set<String> = when {
        group.isSingleChoice -> if (optionId in current && !group.isRequired) emptySet() else setOf(optionId)
        optionId in current -> current - optionId
        group.maxSelections != null && current.size >= group.maxSelections -> current
        else -> current + optionId
    }

    fun invalidGroups(groups: List<OptionGroup>, selections: Map<String, Set<String>>): Set<String> =
        groups.filter { group ->
            val count = selections[group.id].orEmpty().size
            count < group.effectiveMin || (group.maxSelections != null && count > group.maxSelections)
        }.map { it.id }.toSet()
}

class AddToCartUseCase @Inject constructor(
    private val session: OrderSessionRepository,
    private val rules: OptionSelectionRules,
) {
    /** Returns false (and adds nothing) when the selection breaks a group's rules. */
    operator fun invoke(
        product: ProductDetail,
        skuId: String,
        selections: Map<String, Set<String>>,
        quantity: Int,
        note: String?,
    ): Boolean {
        val sku = product.skus.firstOrNull { it.id == skuId } ?: return false
        if (!product.isAvailable || quantity < 1 || rules.invalidGroups(product.optionGroups, selections).isNotEmpty()) return false

        val options = product.optionGroups.flatMap { group ->
            val chosen = selections[group.id].orEmpty()
            group.options.filter { it.id in chosen }.map { SelectedOption(group.id, it.id, it.name, it.priceAdjustment) }
        }
        session.addItem(
            CartItem(
                lineId = UUID.randomUUID().toString(),
                productId = product.id,
                productName = product.name,
                skuId = sku.id,
                skuName = sku.name,
                unitPrice = sku.price,
                quantity = quantity,
                options = options,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = product.imageUrl,
                serviceCharge = product.serviceCharge,
            ),
        )
        return true
    }
}
