package com.pos.kiosk.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatBahtExact
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskConfirmDialog
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskImage
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.QuantityStepper
import com.pos.kiosk.core.ui.SoldOutBadge
import com.pos.kiosk.core.ui.QuantityStepperSize
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.tint
import com.pos.kiosk.domain.model.CartItem
import com.pos.kiosk.domain.model.OrderSession

private val ListShape = RoundedCornerShape(16.dp)

/** Wide landscape screens keep the list and the footer to a readable column instead of stretching edge to edge. */
private val ContentMaxWidth = 960.dp

@Composable
fun CartRoute(onCheckout: () -> Unit, onAddMore: () -> Unit, viewModel: CartViewModel = hiltViewModel()) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    val unavailable by viewModel.unavailableProductIds.collectAsStateWithLifecycle()
    LaunchedEffect(session.cart.isEmpty) { if (session.cart.isEmpty) onAddMore() }
    CartScreen(session, viewModel::changeQuantity, viewModel::remove, onCheckout, onAddMore, unavailableProductIds = unavailable)
}

/**
 * Order review: the title and item count beside the back button; every line in one white list
 * (photo, name, chosen options, the quantity stepper, price and delete); then the estimated total, a note that
 * tax and service charge come at payment, and "Add more" / "Go to payment".
 */
@Composable
fun CartScreen(
    session: OrderSession,
    onQuantity: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
    onAddMore: () -> Unit,
    /** Products sold out (or switched off) since they went in the cart: their lines are flagged and block payment. */
    unavailableProductIds: Set<String> = emptySet(),
) {
    val hasSoldOut = session.cart.items.any { it.productId in unavailableProductIds }
    // The line waiting for "remove?" to be confirmed; saved so a rotation doesn't drop the question.
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(session, onAddMore, Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
            // A kiosk cart holds a handful of lines, so one scrolling column (not a lazy list) can draw them as a single card.
            Surface(shape = ListShape, color = Color.White, shadowElevation = 1.dp, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    session.cart.items.forEachIndexed { index, item ->
                        key(item.lineId) {
                            CartLine(item, onQuantity, onRemove = { pendingRemoval = it }, soldOut = item.productId in unavailableProductIds)
                            if (index < session.cart.items.lastIndex) HorizontalDivider(color = KioskHairline)
                        }
                    }
                }
            }
        }
        CartFooter(session, onCheckout, onAddMore, hasSoldOut)
    }

    session.cart.items.firstOrNull { it.lineId == pendingRemoval }?.let { item ->
        KioskConfirmDialog(
            title = stringResource(R.string.cart_remove_title),
            message = stringResource(R.string.cart_remove_message, item.productName, item.quantity),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                onRemove(item.lineId)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
            icon = KioskIcons.Delete,
            destructive = true,
        )
    }
}

/** Back button (unchanged) with the title and how many items the order has. */
@Composable
private fun Header(session: OrderSession, onBack: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BackCircleButton(onBack)
        Column {
            Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = KioskInk)
            val count = pluralStringResource(R.plurals.menu_items_count, session.cart.itemCount, session.cart.itemCount)
            val member = session.member?.let { stringResource(R.string.cart_member, it.displayName) }
            Text(listOfNotNull(count, member).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = KioskInkMuted)
        }
    }
}

/** Photo, then name / options / stepper; the line total sits top-right and delete bottom-right, level with the stepper. */
@Composable
private fun CartLine(item: CartItem, onQuantity: (String, Int) -> Unit, onRemove: (String) -> Unit, soldOut: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KioskImage(item.imageUrl, item.productName, Modifier.align(Alignment.CenterVertically).size(96.dp).clip(KioskCardShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KioskInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (soldOut) SoldOutBadge()
            val choices = listOfNotNull(item.skuName.takeIf { it != item.productName }) + item.options.map { it.name }
            if (choices.isNotEmpty()) {
                Text(stringResource(R.string.cart_line_options, choices.joinToString(", ")), style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.note?.let {
                Text(stringResource(R.string.cart_line_note, it), style = MaterialTheme.typography.bodyMedium, color = KioskInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // Removing a line is the trash's job, so the stepper stops at 1.
            QuantityStepper(item.quantity, { onQuantity(item.lineId, it) }, size = QuantityStepperSize.Medium)
        }
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.preDiscountTotal?.takeIf { it > item.lineTotal }?.let {
                    Text(formatBahtExact(it), style = MaterialTheme.typography.titleMedium, color = KioskInkMuted, textDecoration = TextDecoration.LineThrough)
                }
                Text(formatBahtExact(item.lineTotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onRemove(item.lineId) }, modifier = Modifier.size(56.dp)) {
                Icon(KioskIcons.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Estimated total in the brand colour, the tax / service charge note, then "Add more" and "Go to payment" side by side. */
@Composable
private fun CartFooter(session: OrderSession, onCheckout: () -> Unit, onAddMore: () -> Unit, hasSoldOut: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = KioskHairline)
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cart_subtotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, modifier = Modifier.weight(1f))
                Text(formatBahtExact(session.cart.subtotal), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            if (hasSoldOut) {
                Text(stringResource(R.string.cart_remove_sold_out_first), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            } else {
                Text(stringResource(R.string.cart_note_estimate), style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KioskOutlinedButton(stringResource(R.string.cart_add_more), onAddMore, Modifier.weight(1f))
                KioskButton(
                    stringResource(R.string.cart_go_to_payment),
                    onCheckout,
                    Modifier.weight(1f),
                    enabled = !session.cart.isEmpty && !hasSoldOut,
                    trailingArrow = true,
                )
            }
        }
    }
}
