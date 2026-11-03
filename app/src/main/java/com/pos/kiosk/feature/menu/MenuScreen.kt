package com.pos.kiosk.feature.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatBaht
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.BannerCarousel
import com.pos.kiosk.core.ui.ErrorPanel
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskImage
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.LoadingPanel
import com.pos.kiosk.core.ui.PillChip
import com.pos.kiosk.core.ui.QuantityStepper
import com.pos.kiosk.core.ui.SoldOutBadge
import com.pos.kiosk.core.ui.QuantityStepperSize
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.tint
import com.pos.kiosk.domain.model.BranchInfo
import com.pos.kiosk.domain.model.Cart
import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.KioskDisplay
import com.pos.kiosk.domain.model.Product
import kotlinx.coroutines.launch

private val PriceInk = Color(0xFF374151)
private val SoldOutSurface = Color(0xFFEEF0F3)
private val CartButtonDisabled = Color(0xFFC4C9D0)

@Composable
fun MenuRoute(onCheckout: () -> Unit, onBack: () -> Unit, viewModel: MenuViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MenuScreen(state, viewModel::onEvent, onCheckout, onBack)
    MenuSheet(viewModel, storeName = state.branch?.businessName)
}

/** Reads the sheet flow in its own scope: option toggles recompose only the dialog, never the grid. */
@Composable
private fun MenuSheet(viewModel: MenuViewModel, storeName: String?) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    sheet?.let { ProductOptionDialog(it, storeName, viewModel::onEvent) }
}

@Composable
fun MenuScreen(state: MenuUiState, onEvent: (MenuEvent) -> Unit, onCheckout: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingPanel()
            state.error != null -> ErrorPanel(state.error, onRetry = { onEvent(MenuEvent.Retry) })
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 1100.dp
                // Banners only on portrait screens: on a landscape kiosk a 3:1 banner would take the height the menu needs.
                val portrait = maxHeight > maxWidth
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // Pinned above the categories (only the grid scrolls): pos-web banners, or without any the welcome strip.
                        if (portrait) TopBanner(state.banners, state.branch, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
                        CategoryBar(state.categories, state.selectedCategoryId, onEvent, onBack)
                        ProductGrid(state.products, state.cartQuantities, onEvent, Modifier.weight(1f))
                        if (!wide) CartBar(state.cart, onCheckout)
                    }
                    if (wide) CartPanel(state.cart, onEvent, onCheckout, Modifier.width(400.dp).fillMaxHeight().padding(16.dp), state.unavailableProductIds)
                }
            }
        }
    }
}

@Composable
private fun TopBanner(banners: KioskDisplay.Banners, branch: BranchInfo?, modifier: Modifier) {
    if (banners.images.isNotEmpty()) BannerCarousel(banners.images, banners.slideSeconds, modifier) else PromoBanner(branch, modifier)
}

/** Welcome strip from the kiosk's own welcome message (pos-web); hidden when none is set. */
@Composable
private fun PromoBanner(branch: BranchInfo?, modifier: Modifier) {
    val message = branch?.welcomeMessage?.takeIf { it.isNotBlank() } ?: return
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.fillMaxWidth().height(120.dp)
            .clip(KioskCardShape)
            .background(Brush.horizontalGradient(listOf(primary.tint(0.22f), primary.tint(0.08f))))
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = lerp(primary, Color.Black, 0.35f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(branch.businessName, style = MaterialTheme.typography.titleMedium, color = KioskInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryBar(categories: List<Category>, selectedCategoryId: String?, onEvent: (MenuEvent) -> Unit, onBack: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BackCircleButton(onBack)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                PillChip(stringResource(R.string.menu_all), selectedCategoryId == null) {
                    onEvent(MenuEvent.SelectCategory(null))
                }
            }
            items(categories, key = { it.id }) { category ->
                PillChip(category.name, selectedCategoryId == category.id) { onEvent(MenuEvent.SelectCategory(category.id)) }
            }
        }
    }
}

/**
 * As many columns of at least [minSize] as fit, never fewer than [minCount]: three big cards on a portrait
 * tablet, four on a 1080 dp kiosk screen, still two on a phone.
 */
private class AdaptiveAtLeast(private val minSize: Dp, private val minCount: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = maxOf(minCount, (availableSize + spacing) / (minSize.roundToPx() + spacing))
        val usable = availableSize - spacing * (count - 1)
        return List(count) { usable / count + if (it < usable % count) 1 else 0 }
    }
}

// One instance: LazyVerticalGrid re-resolves its columns whenever this object changes.
private val MenuGridColumns: GridCells = AdaptiveAtLeast(minSize = 220.dp, minCount = 2)

@Composable
private fun ProductGrid(products: List<Product>, cartQuantities: Map<String, Int>, onEvent: (MenuEvent) -> Unit, modifier: Modifier) {
    if (products.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.menu_empty), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(modifier) {
        LazyVerticalGrid(
            columns = MenuGridColumns,
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag("menu_grid"),
            // Extra bottom room so the last row can scroll clear of the "more below" hint.
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(products, key = { it.id }, contentType = { "product" }) { product ->
                ProductCard(product, inCart = cartQuantities[product.id] ?: 0) { onEvent(MenuEvent.ProductTapped(product)) }
            }
        }
        ScrollMoreHint(gridState, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

/**
 * "Scroll for more" pill while products are hidden below the fold. Tapping it scrolls most of a screen down —
 * and, sitting over the bottom row, it must take the tap itself rather than let it add a product underneath.
 */
@Composable
private fun ScrollMoreHint(gridState: LazyGridState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    // A static chevron on purpose: a looping bounce would make the whole window redraw every frame for as long
    // as the menu is open, which a low-end kiosk tablet feels while scrolling.
    AnimatedVisibility(visible = gridState.canScrollForward, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            onClick = { scope.launch { gridState.animateScrollBy(gridState.layoutInfo.viewportSize.height * 0.8f) } },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.94f),
            contentColor = KioskInkMuted,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.menu_scroll_more), style = MaterialTheme.typography.bodyLarge)
                Icon(KioskIcons.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Product card: photo on top, then name, price and the action — a round "+" for one-tap items, "Choose options"
 * for items with sizes or options. Once the product is in the cart the card gets a brand border and a "✓ 2" mark
 * on the photo (all option variants counted together). Sold-out items are greyed with a "sold out for now" tag.
 * The whole card is the touch target; the action is its visual cue.
 */
@Composable
private fun ProductCard(product: Product, inCart: Int, onClick: () -> Unit) {
    val available = product.isAvailable
    val inCartDescription = if (inCart > 0) pluralStringResource(R.plurals.menu_in_cart, inCart, inCart) else null
    Surface(
        onClick = onClick,
        enabled = available,
        shape = KioskCardShape,
        color = if (available) Color.White else SoldOutSurface,
        shadowElevation = if (available) 2.dp else 0.dp,
        border = if (inCart > 0) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().testTag("product_card")
            .semantics { if (inCartDescription != null) stateDescription = inCartDescription },
    ) {
        Column {
            // Photo runs edge to edge; the card's rounded shape clips its top corners.
            Box(contentAlignment = Alignment.Center) {
                KioskImage(product.imageUrl, product.name, Modifier.fillMaxWidth().aspectRatio(6f / 5f))
                if (!available) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
                    Text(
                        stringResource(R.string.menu_sold_out),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .background(Color(0xFF1F2937).copy(alpha = 0.8f), KioskCardShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), KioskCardShape)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                if (inCart > 0) InCartMark(inCart, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (available) KioskInk else KioskInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The price never shrinks; a long "Choose options" label gives way (ellipsis) on a narrow card.
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatBaht(product.startingPrice),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (available) PriceInk else KioskInkMuted.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (available) {
                        Box(Modifier.weight(1f, fill = false).padding(start = 8.dp)) {
                            if (product.needsCustomization) ChooseOptionsCue() else AddCue()
                        }
                    }
                }
            }
        }
    }
}

/** "✓ 2" in a brand pill on the photo's corner: this product is in the cart, this many times. */
@Composable
private fun InCartMark(count: Int, modifier: Modifier) {
    Row(
        modifier
            .shadow(4.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(KioskIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

/** Round brand-outlined "+" (48 dp, the minimum touch size the card already exceeds). */
@Composable
private fun AddCue() {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(48.dp).border(2.dp, primary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(KioskIcons.Add, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ChooseOptionsCue() {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        stringResource(R.string.menu_choose_options),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.border(1.5.dp, primary, CircleShape).padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Bottom bar on narrower screens: the cart summary on the left, "View cart · total" on the right. */
@Composable
private fun CartBar(cart: Cart, onCheckout: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = KioskHairline)
        // Large type: read at arm's length from a standing customer.
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KioskIcons.ShoppingCart, contentDescription = null, tint = KioskInk, modifier = Modifier.padding(end = 24.dp).size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.menu_your_cart), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (cart.isEmpty) stringResource(R.string.menu_cart_no_items) else pluralStringResource(R.plurals.menu_items_count, cart.itemCount, cart.itemCount),
                    style = MaterialTheme.typography.titleLarge,
                    color = KioskInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KioskButton(
                text = stringResource(R.string.menu_view_cart_total, formatBaht(cart.subtotal)),
                onClick = onCheckout,
                modifier = Modifier.padding(start = 16.dp).widthIn(min = 280.dp, max = 420.dp),
                enabled = !cart.isEmpty,
                trailingArrow = true,
                disabledContainerColor = CartButtonDisabled,
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun CartPanel(cart: Cart, onEvent: (MenuEvent) -> Unit, onCheckout: () -> Unit, modifier: Modifier, unavailableProductIds: Set<String> = emptySet()) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = KioskCardShape,
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, primary.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            // Title with the item count as a pill.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.menu_cart), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, modifier = Modifier.weight(1f))
                if (!cart.isEmpty) {
                    Text(
                        pluralStringResource(R.plurals.menu_items_count, cart.itemCount, cart.itemCount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier.background(primary.tint(0.12f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (cart.isEmpty) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(88.dp).background(primary.tint(0.08f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(KioskIcons.ShoppingCart, contentDescription = null, tint = primary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.menu_cart_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cart.items, key = { it.lineId }) { item ->
                        CartPanelLine(
                            item = item,
                            soldOut = item.productId in unavailableProductIds,
                            onQuantity = { onEvent(MenuEvent.ChangeQuantity(item.lineId, it)) },
                            onRemove = { onEvent(MenuEvent.ChangeQuantity(item.lineId, 0)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFEDEDF0))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cart_subtotal), style = MaterialTheme.typography.titleMedium, color = KioskInk, modifier = Modifier.weight(1f))
                Text(formatBaht(cart.subtotal), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = primary)
            }
            Spacer(Modifier.height(16.dp))
            KioskButton(stringResource(R.string.menu_view_cart), onCheckout, Modifier.fillMaxWidth(), enabled = !cart.isEmpty, trailingArrow = true, disabledContainerColor = CartButtonDisabled)
        }
    }
}

/** One cart row: photo, name + price, options one per line, then the stepper on the left and the trash on the right. */
@Composable
private fun CartPanelLine(item: CartItem, onQuantity: (Int) -> Unit, onRemove: () -> Unit, soldOut: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(Modifier.fillMaxWidth(), shape = KioskCardShape, color = Color(0xFFF7F7F9)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KioskImage(item.imageUrl, item.productName, Modifier.size(64.dp).clip(KioskCardShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KioskInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatBaht(item.lineTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primary, maxLines = 1)
                }
                if (soldOut) SoldOutBadge(Modifier.padding(vertical = 2.dp))
                val details = listOfNotNull(item.skuName.takeIf { it != item.productName }) + item.options.map { it.name }
                details.forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280)) }
                item.note?.let { Text("* $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(item.quantity, onQuantity, size = QuantityStepperSize.Small)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                        Icon(KioskIcons.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}
