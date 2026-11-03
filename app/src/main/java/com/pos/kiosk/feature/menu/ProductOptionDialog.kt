package com.pos.kiosk.feature.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatBaht
import com.pos.kiosk.core.session.reportUserInteraction
import com.pos.kiosk.core.ui.ErrorPanel
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskImage
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.KioskMutedSurface
import com.pos.kiosk.core.ui.LoadingPanel
import com.pos.kiosk.core.ui.QuantityStepper
import com.pos.kiosk.core.ui.currentWindowHeight
import com.pos.kiosk.core.ui.currentWindowWidth
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.tint
import com.pos.kiosk.domain.model.ProductDetail
import kotlin.math.roundToInt

private val DialogShape = RoundedCornerShape(24.dp)
private val PhotoShape = RoundedCornerShape(16.dp)

/**
 * Product options as one white card over the menu: the store name and a close button, a large photo, the
 * name and base price, one section per choice group (sizes first) with pill-shaped choices, an optional
 * note to the kitchen, then a footer with the price per item, the quantity and "Add to cart · total".
 * "Add to cart" never silently fails: a group that breaks its rule turns red, says what is missing and the
 * card scrolls to it.
 */
@Composable
fun ProductOptionDialog(sheet: OptionSheetState, storeName: String?, onEvent: (MenuEvent) -> Unit) {
    val english = LocalConfiguration.current.locales[0].language == "en"
    // Read in the activity's composition: inside Dialog { } the window info is the dialog's own window.
    val photoMaxHeight = currentWindowHeight() * 0.3f
    val compact = currentWindowWidth() < 560.dp
    Dialog(onDismissRequest = { onEvent(MenuEvent.DismissSheet) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = DialogShape,
            color = Color.White,
            shadowElevation = 12.dp,
            // A dialog is its own window/composition, so it needs its own testTagsAsResourceId.
            modifier = Modifier.reportUserInteraction()
                .semantics { testTagsAsResourceId = true }
                .testTag("option_dialog")
                .widthIn(max = 760.dp).fillMaxWidth().padding(24.dp),
        ) {
            Column {
                Header(storeName, onClose = { onEvent(MenuEvent.DismissSheet) })
                val product = sheet.product
                when {
                    sheet.loading -> LoadingPanel(Modifier.heightIn(max = 320.dp))
                    sheet.error != null || product == null ->
                        ErrorPanel(sheet.error ?: return@Column, onRetry = null, modifier = Modifier.heightIn(max = 320.dp))
                    else -> SheetContent(sheet, product, english, photoMaxHeight, compact, onEvent)
                }
            }
        }
    }
}

@Composable
private fun SheetContent(
    sheet: OptionSheetState,
    product: ProductDetail,
    english: Boolean,
    photoMaxHeight: Dp,
    compact: Boolean,
    onEvent: (MenuEvent) -> Unit,
) {
    val scroll = rememberScrollState()
    val groupTops = remember { mutableStateMapOf<String, Int>() }

    // A rejected "add to cart" brings the first offending group into view.
    LaunchedEffect(sheet.validationTick) {
        if (sheet.validationTick == 0) return@LaunchedEffect
        val firstInvalid = product.optionGroups.firstOrNull { it.id in sheet.invalidGroupIds }?.id ?: return@LaunchedEffect
        groupTops[firstInvalid]?.let { scroll.animateScrollTo((it - 16).coerceAtLeast(0)) }
    }

    Column {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // No photo, no empty grey block: the card just starts with the name.
            product.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                KioskImage(url, product.name, Modifier.photoSize(photoMaxHeight).clip(PhotoShape))
            }
            TitleRow(sheet, product)

            if (product.skus.size > 1) {
                ChoiceSection(
                    title = stringResource(R.string.option_sku_title),
                    rule = ruleText(min = 1, max = 1),
                    invalidMessage = null,
                    choices = product.skus.map { sku -> Choice(sku.id, sku.name, formatBaht(sku.price), sheet.selectedSkuId == sku.id, true) },
                    multiple = false,
                    onChoiceClick = { skuId -> onEvent(MenuEvent.SelectSku(skuId)) },
                )
            }

            product.optionGroups.forEach { group ->
                val selected = sheet.selections[group.id].orEmpty()
                val atMax = group.maxSelections != null && selected.size >= group.maxSelections
                ChoiceSection(
                    title = group.displayName(english),
                    rule = ruleText(group.effectiveMin, group.maxSelections),
                    invalidMessage = if (group.id in sheet.invalidGroupIds) stringResource(R.string.option_error_pick, group.effectiveMin) else null,
                    choices = group.options.map { option ->
                        val isSelected = option.id in selected
                        Choice(
                            id = option.id,
                            label = option.displayName(english),
                            // Word joiner: a narrow pill may wrap before "+฿15", never between "+" and "฿15".
                            price = if (option.priceAdjustment > 0) "+\u2060" + formatBaht(option.priceAdjustment) else null,
                            selected = isSelected,
                            // Once a multi-choice group is full the remaining options are greyed out.
                            enabled = group.isSingleChoice || isSelected || !atMax,
                        )
                    },
                    multiple = !group.isSingleChoice,
                    onChoiceClick = { optionId -> onEvent(MenuEvent.ToggleOption(group.id, optionId)) },
                    modifier = Modifier.onGloballyPositioned { groupTops[group.id] = it.positionInParent().y.roundToInt() },
                )
            }

            NoteSection(sheet.note) { onEvent(MenuEvent.SheetNote(it)) }
        }
        Footer(sheet, product, compact, onEvent)
    }
}

/** Full width, 16:10, but never taller than [maxHeight] so the choices stay in reach on a landscape screen. */
private fun Modifier.photoSize(maxHeight: Dp): Modifier = fillMaxWidth().layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = minOf((width / 1.6f).roundToInt(), maxHeight.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

@Composable
private fun Header(storeName: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            storeName.orEmpty().uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = KioskInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Surface(onClick = onClose, shape = CircleShape, color = KioskMutedSurface, modifier = Modifier.padding(start = 12.dp).size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(KioskIcons.Close, contentDescription = stringResource(R.string.action_close), tint = KioskInk, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Name (and description) on the left, the selected size's base price on the right. */
@Composable
private fun TitleRow(sheet: OptionSheetState, product: ProductDetail) {
    val basePrice = product.skus.firstOrNull { it.id == sheet.selectedSkuId }?.price ?: product.skus.minOfOrNull { it.price }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            product.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (basePrice != null) {
            Text(formatBaht(basePrice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** Plain data (no click lambda), so an unchanged section compares equal and is skipped. */
private data class Choice(
    val id: String,
    val label: String,
    val price: String?,
    val selected: Boolean,
    val enabled: Boolean,
)

/** Title, the rule ("Choose 1" / "Optional") or what is missing in red, then the choices in equal-width columns. */
@Composable
private fun ChoiceSection(
    title: String,
    rule: String,
    invalidMessage: String?,
    choices: List<Choice>,
    multiple: Boolean,
    onChoiceClick: (choiceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KioskInk)
            Text(
                invalidMessage ?: rule,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (invalidMessage != null) FontWeight.Bold else FontWeight.Normal,
                color = if (invalidMessage != null) MaterialTheme.colorScheme.error else KioskInkMuted,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            // Short single choices fit four abreast (like sweetness levels); a check mark or a price needs more room.
            val minWidth = if (multiple || choices.any { it.price != null }) 200.dp else 140.dp
            val columns = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceIn(1, 4)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                choices.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { choice ->
                            ChoiceChip(choice, multiple, { onChoiceClick(choice.id) }, Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * White pill with a hairline border; selected = brand border, brand-tinted fill and bold brand text.
 * Multiple-choice pills lead with a round check so they read as "pick any". Radio / checkbox semantics
 * come from the selectable / toggleable Surface overloads.
 */
@Composable
private fun ChoiceChip(choice: Choice, multiple: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = when {
        !choice.enabled -> KioskInk.copy(alpha = 0.35f)
        choice.selected -> primary
        else -> KioskInk
    }
    val color = if (choice.selected) primary.tint(0.08f) else Color.White
    val border = BorderStroke(if (choice.selected) 1.5.dp else 1.dp, if (choice.selected) primary else KioskHairline)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, if (multiple) Alignment.Start else Alignment.CenterHorizontally),
        ) {
            if (multiple) CheckMark(choice.selected, choice.enabled)
            Text(
                listOfNotNull(choice.label, choice.price).joinToString(" "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (choice.selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = if (multiple) TextAlign.Start else TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val chipModifier = modifier.heightIn(min = 60.dp)
    if (multiple) {
        Surface(checked = choice.selected, onCheckedChange = { onClick() }, enabled = choice.enabled, shape = KioskCardShape, color = color, border = border, modifier = chipModifier, content = content)
    } else {
        Surface(selected = choice.selected, onClick = onClick, enabled = choice.enabled, shape = KioskCardShape, color = color, border = border, modifier = chipModifier, content = content)
    }
}

/** Filled brand circle with a white check when picked, an empty grey ring otherwise. */
@Composable
private fun CheckMark(selected: Boolean, enabled: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    if (selected) {
        Box(Modifier.size(26.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(KioskIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    } else {
        Box(Modifier.size(26.dp).border(2.dp, Color(0xFF9CA3AF).copy(alpha = if (enabled) 1f else 0.5f), CircleShape))
    }
}

/** Note to the kitchen: same title/rule layout as a choice section, then one outlined line. */
@Composable
private fun NoteSection(note: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.option_note), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KioskInk)
            Text(stringResource(R.string.option_optional), style = MaterialTheme.typography.bodyMedium, color = KioskInkMuted)
        }
        OutlinedTextField(
            value = note,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.option_note_placeholder), color = KioskInkMuted.copy(alpha = 0.7f)) },
            singleLine = true,
            shape = KioskCardShape,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = KioskHairline),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Price per item, then the quantity stepper and "Add to cart · total" side by side (stacked on a phone). */
@Composable
private fun Footer(sheet: OptionSheetState, product: ProductDetail, compact: Boolean, onEvent: (MenuEvent) -> Unit) {
    val addToCart = stringResource(R.string.option_add_to_cart, formatBaht(sheet.total))
    Column(Modifier.background(Color.White)) {
        HorizontalDivider(color = KioskHairline, modifier = Modifier.padding(horizontal = 24.dp))
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.option_unit_price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KioskInk, modifier = Modifier.weight(1f))
                Text(formatBaht(sheet.unitPrice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk)
            }
            val stepper = @Composable { QuantityStepper(sheet.quantity, { onEvent(MenuEvent.SheetQuantity(it)) }) }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    stepper()
                    KioskButton(addToCart, { onEvent(MenuEvent.ConfirmSheet) }, Modifier.fillMaxWidth(), enabled = product.isAvailable)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    stepper()
                    KioskButton(addToCart, { onEvent(MenuEvent.ConfirmSheet) }, Modifier.weight(1f), enabled = product.isAvailable)
                }
            }
        }
    }
}

/** "Choose 1" / "Choose 1–3" / "Choose at least 2" / "Optional" / "Optional · up to 3" — what the group's min/max mean. */
@Composable
private fun ruleText(min: Int, max: Int?): String =
    if (max == null) {
        if (min == 0) stringResource(R.string.option_optional) else stringResource(R.string.option_hint_at_least, min)
    } else {
        when {
            min == 0 -> stringResource(R.string.option_optional_up_to, max)
            min == max -> stringResource(R.string.option_hint_exact, min)
            else -> stringResource(R.string.option_hint_range, min, max)
        }
    }
