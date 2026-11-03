package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.KioskTheme

/** Heights that line up with what sits next to the stepper. */
enum class QuantityStepperSize(val height: Dp, val keyWidth: Dp, val valueWidth: Dp, val iconSize: Dp) {
    /** Beside a 72 dp [KioskButton] (the option sheet's footer). */
    Large(height = 72.dp, keyWidth = 64.dp, valueWidth = 64.dp, iconSize = 28.dp),

    /** Order review rows. */
    Medium(height = 56.dp, keyWidth = 56.dp, valueWidth = 52.dp, iconSize = 24.dp),

    /** Narrow side panels (the menu's wide-screen cart). */
    Small(height = 44.dp, keyWidth = 44.dp, valueWidth = 40.dp, iconSize = 20.dp),
}

/**
 * The kiosk's one quantity control: [ − | 2 | + ] in a single hairline box, grey keys on the sides.
 * The value stays within [min]..[max]; the key at a bound is disabled rather than hidden. Each key is a labelled
 * button for TalkBack and the number reads as "Quantity 2".
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
    size: QuantityStepperSize = QuantityStepperSize.Large,
) {
    val valueDescription = stringResource(R.string.quantity_value, quantity)
    Row(
        // White behind the number, so the stepper looks the same on a white card and on a grey row.
        modifier.height(size.height).clip(KioskCardShape).background(Color.White).border(1.dp, KioskHairline, KioskCardShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey(KioskIcons.Remove, stringResource(R.string.action_decrease_quantity), enabled = quantity > min, size = size) {
            onQuantityChange(quantity - 1)
        }
        Text(
            quantity.toString(),
            style = valueStyle(size),
            fontWeight = FontWeight.Bold,
            color = KioskInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(size.valueWidth).semantics { contentDescription = valueDescription },
        )
        StepperKey(KioskIcons.Add, stringResource(R.string.action_increase_quantity), enabled = quantity < max, size = size) {
            onQuantityChange(quantity + 1)
        }
    }
}

@Composable
private fun valueStyle(size: QuantityStepperSize): TextStyle = when (size) {
    QuantityStepperSize.Large -> MaterialTheme.typography.headlineSmall
    QuantityStepperSize.Medium -> MaterialTheme.typography.titleLarge
    QuantityStepperSize.Small -> MaterialTheme.typography.titleMedium
}

@Composable
private fun StepperKey(icon: ImageVector, description: String, enabled: Boolean, size: QuantityStepperSize, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(size.keyWidth)
            .background(KioskMutedSurface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = KioskInk.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(size.iconSize))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun QuantityStepperPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QuantityStepperSize.entries.forEach { size ->
                var quantity by remember { mutableIntStateOf(if (size == QuantityStepperSize.Medium) 2 else 1) }
                QuantityStepper(quantity, { quantity = it }, size = size)
            }
        }
    }
}
