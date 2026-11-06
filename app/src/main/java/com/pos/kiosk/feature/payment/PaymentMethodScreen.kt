package com.pos.kiosk.feature.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatBahtExact
import com.pos.kiosk.core.common.formatRate
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.ChoiceCard
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.messageRes
import com.pos.kiosk.domain.model.OrderTotals
import com.pos.kiosk.domain.model.PaymentMethod

@Composable
fun PaymentMethodRoute(
    onCashPlaced: (orderId: String) -> Unit,
    onPromptPayPlaced: (orderId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: PaymentMethodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PaymentMethodEffect.OrderPlaced ->
                if (effect.order.paymentMethod == PaymentMethod.PROMPTPAY) onPromptPayPlaced(effect.order.orderId) else onCashPlaced(effect.order.orderId)
            PaymentMethodEffect.BackToCart -> onBack()
        }
    }
    PaymentMethodScreen(state, viewModel::select, viewModel::back)
}

/**
 * The amount due with its breakdown (service charge and VAT when they apply), then the payment methods as the
 * same square cards as the order type on the first screen. Choosing one places the order.
 */
@Composable
fun PaymentMethodScreen(state: PaymentMethodUiState, onSelect: (PaymentMethod) -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp)) {
        if (state.placing == null) Box(Modifier.align(Alignment.TopStart)) { BackCircleButton(onBack) }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.payment_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = KioskInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            TotalsCard(state.totals)
            Spacer(Modifier.height(32.dp))

            val methods = buildList {
                if (state.cashEnabled) add(Method(PaymentMethod.CASH, KioskIcons.Payments, R.string.payment_cash, R.string.payment_cash_desc))
                if (state.promptPayEnabled) add(Method(PaymentMethod.PROMPTPAY, KioskIcons.QrCode2, R.string.payment_promptpay, R.string.payment_promptpay_desc))
            }
            // Side by side like the order type cards; a lone method keeps the same card size, centred.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (methods.size == 1) Spacer(Modifier.weight(0.5f))
                methods.forEachIndexed { index, method ->
                    ChoiceCard(
                        title = stringResource(method.title),
                        subtitle = stringResource(method.subtitle),
                        icon = method.icon,
                        filled = false,
                        onClick = { onSelect(method.method) },
                        enabled = state.placing == null,
                        loading = state.placing == method.method,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (methods.size == 1) Spacer(Modifier.weight(0.5f))
            }

            Spacer(Modifier.height(24.dp))
            if (state.placing != null) Text(stringResource(R.string.payment_placing), style = MaterialTheme.typography.titleMedium)
            state.error?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

private data class Method(val method: PaymentMethod, val icon: ImageVector, val title: Int, val subtitle: Int)

/**
 * Food, then what is added on top (service charge, VAT when prices exclude it), then the amount due in large type.
 * With VAT-inclusive prices the VAT is part of the amount, so it is shown under the total as "incl. VAT".
 * With nothing added on top, the food line would just repeat the total and is left out.
 */
@Composable
private fun TotalsCard(totals: OrderTotals) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Prices include VAT: no VAT line at all (it's already in every price). Prices exclude VAT: the amount
            // before VAT, then VAT on top. Food has its own line only when a service charge makes up part of it.
            if (totals.hasExtras) {
                if (!totals.vatAddedOnTop || totals.serviceCharge > 0) AmountRow(stringResource(R.string.payment_food_total), totals.subtotal)
                if (totals.serviceCharge > 0) AmountRow(stringResource(R.string.payment_service_charge, formatRate(totals.serviceChargeRate)), totals.serviceCharge)
                if (totals.vatAddedOnTop) {
                    AmountRow(stringResource(R.string.payment_before_vat), totals.beforeTax)
                    AmountRow(stringResource(R.string.payment_vat, formatRate(totals.taxRate)), totals.tax)
                }
                HorizontalDivider(color = KioskHairline, modifier = Modifier.padding(vertical = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.payment_total_due), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = KioskInk, modifier = Modifier.weight(1f))
                Text(formatBahtExact(totals.total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = KioskInkMuted, modifier = Modifier.weight(1f))
        Text(formatBahtExact(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = KioskInk)
    }
}
