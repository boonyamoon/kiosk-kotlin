package com.pos.kiosk.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.common.roundMoney
import com.pos.kiosk.domain.model.BranchInfo
import com.pos.kiosk.domain.model.KioskDisplay
import com.pos.kiosk.domain.model.Cart
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.model.Product
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.usecase.AddToCartUseCase
import com.pos.kiosk.domain.usecase.OptionSelectionRules
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class OptionSheetState(
    /** The product that was tapped; a late detail response for another product is dropped. */
    val productId: String = "",
    val loading: Boolean = true,
    val product: ProductDetail? = null,
    val selectedSkuId: String? = null,
    val selections: Map<String, Set<String>> = emptyMap(),
    val quantity: Int = 1,
    val note: String = "",
    val invalidGroupIds: Set<String> = emptySet(),
    /** Bumped on every rejected "add to cart" so the dialog can scroll to the first group that needs attention. */
    val validationTick: Int = 0,
    val error: AppError? = null,
) {
    val unitPrice: Double
        get() {
            val product = product ?: return 0.0
            val skuPrice = product.skus.firstOrNull { it.id == selectedSkuId }?.price ?: 0.0
            val optionsPrice = product.optionGroups.sumOf { group ->
                group.options.filter { it.id in selections[group.id].orEmpty() }.sumOf { it.priceAdjustment }
            }
            return roundMoney(skuPrice + optionsPrice)
        }
    val total: Double get() = roundMoney(unitPrice * quantity)
}

/**
 * Screen state for the grid, category bar and cart. The option sheet is a separate flow
 * ([MenuViewModel.sheet]) so toggling an option never rebuilds this state or recomposes the grid.
 */
@Immutable
data class MenuUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    /** Same list instance until the products or the selected category change, so the grid can skip. */
    val products: List<Product> = emptyList(),
    val cart: Cart = Cart(),
    /** Units of each product already in the cart, so its card can show "✓ 2". */
    val cartQuantities: Map<String, Int> = emptyMap(),
    val member: Member? = null,
    val branch: BranchInfo? = null,
    /** Menu-page promotions from pos-web /kiosk-settings (none: the welcome banner shows instead). */
    val banners: KioskDisplay.Banners = KioskDisplay.Banners(),
    /** Products that can't be ordered right now (polled every ~20 s), for the wide-screen cart panel's badges. */
    val unavailableProductIds: Set<String> = emptySet(),
)

sealed interface MenuEvent {
    data class SelectCategory(val categoryId: String?) : MenuEvent
    data class ProductTapped(val product: Product) : MenuEvent
    data class SelectSku(val skuId: String) : MenuEvent
    data class ToggleOption(val groupId: String, val optionId: String) : MenuEvent
    data class SheetQuantity(val quantity: Int) : MenuEvent
    data class SheetNote(val note: String) : MenuEvent
    data object ConfirmSheet : MenuEvent
    data object DismissSheet : MenuEvent
    data class ChangeQuantity(val lineId: String, val quantity: Int) : MenuEvent
    data object Retry : MenuEvent
}

private data class MenuData(
    val loading: Boolean = true,
    val error: AppError? = null,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: String? = null,
    /** Filtered once per category change / reload, not on every cart or option event. */
    val visibleProducts: List<Product> = emptyList(),
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menu: MenuRepository,
    private val config: ConfigRepository,
    private val session: OrderSessionRepository,
    private val addToCart: AddToCartUseCase,
    private val rules: OptionSelectionRules,
) : ViewModel() {

    private val data = MutableStateFlow(MenuData())
    private val _sheet = MutableStateFlow<OptionSheetState?>(null)
    val sheet: StateFlow<OptionSheetState?> = _sheet.asStateFlow()

    val state: StateFlow<MenuUiState> = combine(data, session.session, config.config, menu.unavailableProductIds) { d, s, c, unavailable ->
        MenuUiState(
            loading = d.loading,
            error = d.error,
            categories = d.categories,
            selectedCategoryId = d.selectedCategoryId,
            products = d.visibleProducts.withAvailability(unavailable),
            unavailableProductIds = unavailable,
            cart = s.cart,
            cartQuantities = s.cart.quantitiesByProduct(),
            member = s.member,
            branch = c?.branch,
            banners = c?.display?.banners ?: KioskDisplay.Banners(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuUiState())

    // Declared before init: init calls load(), which assigns loadJob.
    private var loadJob: Job? = null
    private var detailJob: Job? = null

    init {
        load()
        // An item that sells out while its option sheet is open: say so there, and the add button stops working.
        viewModelScope.launch {
            menu.unavailableProductIds.collect { unavailable ->
                _sheet.update { sheet -> if (sheet != null && sheet.productId in unavailable) sheet.copy(error = AppError.ProductSoldOut) else sheet }
            }
        }
    }

    fun onEvent(event: MenuEvent) {
        when (event) {
            is MenuEvent.SelectCategory -> data.update { it.withCategory(event.categoryId) }
            is MenuEvent.ProductTapped -> onProductTapped(event.product)
            is MenuEvent.SelectSku -> updateSheet { it.copy(selectedSkuId = event.skuId) }
            is MenuEvent.ToggleOption -> updateSheet { sheet ->
                val group = sheet.product?.optionGroups?.firstOrNull { it.id == event.groupId } ?: return@updateSheet sheet
                val toggled = rules.toggle(group, sheet.selections[group.id].orEmpty(), event.optionId)
                sheet.copy(selections = sheet.selections + (group.id to toggled), invalidGroupIds = sheet.invalidGroupIds - group.id)
            }
            is MenuEvent.SheetQuantity -> updateSheet { it.copy(quantity = event.quantity.coerceIn(1, 99)) }
            is MenuEvent.SheetNote -> updateSheet { it.copy(note = event.note.take(120)) }
            MenuEvent.ConfirmSheet -> confirmSheet()
            MenuEvent.DismissSheet -> closeSheet()
            is MenuEvent.ChangeQuantity -> session.updateQuantity(event.lineId, event.quantity)
            MenuEvent.Retry -> load()
        }
    }

    /** Categories and products load in parallel; a retry while loading is ignored. */
    private fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            val (categories, products) = coroutineScope {
                val categories = async { menu.categories() }
                val products = async { menu.products() }
                categories.await() to products.await()
            }
            if (categories is AppResult.Success && products is AppResult.Success) {
                val usedCategoryIds = products.data.mapNotNull { it.categoryId }.toSet()
                data.update {
                    it.copy(
                        loading = false,
                        categories = categories.data.filter { c -> c.id in usedCategoryIds },
                        products = products.data,
                    ).withCategory(it.selectedCategoryId)
                }
            } else {
                val error = (categories as? AppResult.Failure)?.error ?: (products as AppResult.Failure).error
                data.update { it.copy(loading = false, error = error) }
            }
        }
    }

    private fun MenuData.withCategory(categoryId: String?) = copy(
        selectedCategoryId = categoryId,
        visibleProducts = if (categoryId == null) products else products.filter { it.categoryId == categoryId },
    )

    private fun onProductTapped(product: Product) {
        if (!product.isAvailable) return
        if (!product.needsCustomization) {
            val sku = product.defaultSku ?: return
            addToCart(product.asDetail(), sku.id, emptyMap(), 1, null)
            return
        }
        detailJob?.cancel()
        _sheet.value = OptionSheetState(productId = product.id, loading = true)
        detailJob = viewModelScope.launch {
            val detail = menu.productDetail(product.id)
            // Only fill the sheet if it is still the one for this product (not dismissed / replaced meanwhile).
            _sheet.update { current ->
                if (current?.productId != product.id) return@update current
                when (detail) {
                    is AppResult.Success -> {
                        val p = detail.data
                        OptionSheetState(
                            productId = product.id,
                            loading = false,
                            product = p,
                            selectedSkuId = (p.skus.firstOrNull { s -> s.isDefault } ?: p.skus.firstOrNull())?.id,
                            selections = rules.defaults(p.optionGroups),
                        )
                    }
                    is AppResult.Failure -> current.copy(loading = false, error = detail.error)
                }
            }
        }
    }

    private fun closeSheet() {
        detailJob?.cancel()
        _sheet.value = null
    }

    private fun confirmSheet() {
        val sheet = _sheet.value ?: return
        if (sheet.productId in menu.unavailableProductIds.value) {
            updateSheet { it.copy(error = AppError.ProductSoldOut) }
            return
        }
        val product = sheet.product ?: return
        val skuId = sheet.selectedSkuId ?: return
        val invalid = rules.invalidGroups(product.optionGroups, sheet.selections)
        if (invalid.isNotEmpty()) {
            updateSheet { it.copy(invalidGroupIds = invalid, validationTick = it.validationTick + 1) }
            return
        }
        if (addToCart(product, skuId, sheet.selections, sheet.quantity, sheet.note)) {
            closeSheet()
        }
    }

    private fun updateSheet(transform: (OptionSheetState) -> OptionSheetState) =
        _sheet.update { current -> current?.let(transform) }

    private fun Product.asDetail() = ProductDetail(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        isAvailable = isAvailable,
        skus = skus,
        optionGroups = emptyList(),
        serviceCharge = serviceCharge,
    )
}

/**
 * The menu's own flag replaced by the latest availability (full load or ~20 s poll), which is authoritative both
 * ways: sold out since the load, or back in stock. Unchanged products (and an unchanged list) keep their instance.
 */
internal fun List<Product>.withAvailability(unavailable: Set<String>): List<Product> {
    // Nothing changed (the usual case on cart and option events): the same list instance, so the grid skips.
    if (none { it.isAvailable == (it.id in unavailable) }) return this
    return map { product ->
        val available = product.id !in unavailable
        if (product.isAvailable == available) product else product.copy(isAvailable = available)
    }
}
