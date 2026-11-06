package com.pos.kiosk.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pos.kiosk.R
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.ui.icons.KioskIcons

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    AppError.InvalidCredentials -> R.string.error_login_failed
    AppError.NotAllowed -> R.string.error_not_allowed
    AppError.TerminalDisabled, AppError.TerminalRevoked -> R.string.oos_title
    is AppError.OutOfStock -> R.string.error_out_of_stock
    AppError.ProductSoldOut -> R.string.menu_item_just_sold_out
    AppError.OutsideHours -> R.string.error_outside_hours
    AppError.OrderTypeUnavailable -> R.string.error_order_type_unavailable
    AppError.PromptPayUnavailable -> R.string.error_promptpay_unavailable
    AppError.PaymentMethodDisabled -> R.string.error_payment_method_disabled
    AppError.KioskNotEnabled -> R.string.error_kiosk_not_enabled
    AppError.AlreadyPaid -> R.string.error_already_paid
    AppError.OrderInKitchen -> R.string.error_order_in_kitchen
    AppError.NoKioskTerminal -> R.string.setup_no_terminal
    AppError.PrinterUnavailable -> R.string.success_print_failed
    AppError.PrinterOutOfPaper -> R.string.success_print_no_paper
    AppError.InvalidPin -> R.string.admin_pin_invalid
    AppError.WeakPin -> R.string.admin_pin_weak
    AppError.TooManyPinAttempts -> R.string.admin_pin_locked
    is AppError.Server -> R.string.error_generic
}

/** The one corner radius used by every card, key and sheet in the app. */
val KioskCardShape = RoundedCornerShape(10.dp)

/** Ink colour used for text on white cards; the brand colour is reserved for actions and prices. */
val KioskInk = Color(0xFF2B2B2B)

/** Secondary text (prices, hints) on white cards. */
val KioskInkMuted = Color(0xFF6B7280)

/** 1 dp borders and separators on white. */
val KioskHairline = Color(0xFFE5E7EB)

/** Light grey fill for secondary controls on white (stepper keys, round close buttons). */
val KioskMutedSurface = Color(0xFFF1F3F5)

@Composable
fun KioskButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingArrow: Boolean = false,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Unspecified = the brand colour at 40 %. */
    disabledContainerColor: Color = Color.Unspecified,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    /** Unspecified = the brand colour; e.g. the error colour for a destructive action. */
    containerColor: Color = Color.Unspecified,
) {
    val primary = MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor.takeOrElse { primary },
            contentColor = Color.White,
            disabledContainerColor = disabledContainerColor.takeOrElse { primary.copy(alpha = 0.4f) },
            disabledContentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp, disabledElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = textStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            if (trailingArrow) {
                Spacer(Modifier.width(12.dp))
                Icon(KioskIcons.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun KioskOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = primary),
        border = BorderStroke(1.5.dp, primary.copy(alpha = if (enabled) 1f else 0.3f)),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** White card with a soft shadow — the one card style used on every ordering screen. */
@Composable
fun KioskCard(modifier: Modifier = Modifier, containerColor: Color = Color.White, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = KioskCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) { content() }
}

/** Pill-shaped selectable chip: filled with the brand colour when selected, white with a hairline border otherwise. */
@Composable
fun PillChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (selected) Color.White else KioskInk,
        border = if (selected) null else BorderStroke(1.dp, KioskHairline),
        shadowElevation = if (selected) 4.dp else 1.dp,
        modifier = modifier.heightIn(min = 56.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Round white back button shared by every screen that can step back. */
@Composable
fun BackCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(KioskIcons.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = KioskInk)
        }
    }
}

@Composable
fun PhoneNumberPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 80.dp) {
    val rows = listOf("123", "456", "789")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> PadKey(digit.toString(), Modifier.weight(1f).height(keyHeight)) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            PadKey("0", Modifier.weight(1f).height(keyHeight)) { onDigit('0') }
            FilledTonalIconButton(
                onClick = onBackspace,
                modifier = Modifier.weight(1f).height(keyHeight),
                shape = KioskCardShape,
            ) {
                Icon(KioskIcons.Backspace, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = KioskCardShape, color = Color.White, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk)
        }
    }
}

@Composable
fun LoadingPanel(modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            if (message != null) {
                Spacer(Modifier.height(24.dp))
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ErrorPanel(error: AppError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(error.messageRes()),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(24.dp))
                KioskButton(stringResource(R.string.action_retry), onRetry, modifier = Modifier.width(280.dp))
            }
        }
    }
}
