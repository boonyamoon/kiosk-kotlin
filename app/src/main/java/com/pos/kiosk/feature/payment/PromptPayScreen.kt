package com.pos.kiosk.feature.payment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.common.formatBahtExact
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.messageRes

@Composable
fun PromptPayRoute(
    onCompleted: (orderId: String) -> Unit,
    onBackToCart: () -> Unit,
    onAbandoned: () -> Unit,
    viewModel: PromptPayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PromptPayEffect.Completed -> onCompleted(effect.orderId)
            PromptPayEffect.BackToCart -> onBackToCart()
            PromptPayEffect.Abandoned -> onAbandoned()
        }
    }
    // Leaving must go through "cancel order" so the HELD order and QR are released.
    BackHandler(enabled = true) { }
    PromptPayScreen(state, { secondsLeft }, viewModel::newQr, viewModel::payAtCounter, viewModel::cancelOrder)
}

@Composable
fun PromptPayScreen(state: PromptPayUiState, secondsLeft: () -> Long, onNewQr: () -> Unit, onPayAtCounter: () -> Unit, onCancel: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        val sideBySide = maxHeight < 760.dp && maxWidth > maxHeight
        val qrSize = minOf(420.dp, if (sideBySide) maxHeight - 32.dp else maxWidth - 32.dp)
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { QrArea(state, qrSize) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Details(state, secondsLeft, onNewQr, onPayAtCounter, onCancel)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                QrArea(state, qrSize)
                Details(state, secondsLeft, onNewQr, onPayAtCounter, onCancel)
            }
        }
    }
}

@Composable
private fun QrArea(state: PromptPayUiState, size: Dp) {
    when {
        state.phase == PromptPayPhase.SHOWING && state.qr != null ->
            Box(Modifier.background(Color.White, KioskCardShape).padding(16.dp)) {
                Image(state.qr, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(size))
            }
        state.phase == PromptPayPhase.CREATING || state.phase == PromptPayPhase.WORKING ->
            CircularProgressIndicator(Modifier.size(96.dp))
    }
}

@Composable
private fun ColumnScope.Details(state: PromptPayUiState, secondsLeft: () -> Long, onNewQr: () -> Unit, onPayAtCounter: () -> Unit, onCancel: () -> Unit) {
    Text(stringResource(R.string.promptpay_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    if (state.amount > 0) Text(stringResource(R.string.promptpay_amount, formatBahtExact(state.amount)), style = MaterialTheme.typography.displayMedium)

    when (state.phase) {
        PromptPayPhase.SHOWING -> {
            ExpiresInText(secondsLeft)
            Text(stringResource(R.string.promptpay_waiting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        }
        PromptPayPhase.EXPIRED -> {
            Text(
                state.error?.let { stringResource(it.messageRes()) } ?: stringResource(R.string.promptpay_expired),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            KioskButton(stringResource(R.string.promptpay_new_qr), onNewQr, Modifier.fillMaxWidth())
            if (state.payAtCounterAllowed) {
                KioskOutlinedButton(stringResource(R.string.promptpay_pay_at_counter), onPayAtCounter, Modifier.fillMaxWidth())
            }
        }
        PromptPayPhase.CREATING, PromptPayPhase.WORKING -> Unit
    }

    if (state.phase == PromptPayPhase.SHOWING || state.phase == PromptPayPhase.EXPIRED) {
        KioskOutlinedButton(stringResource(R.string.promptpay_cancel_order), onCancel, Modifier.fillMaxWidth())
    }
}

/** Reads the countdown itself (deferred read), so each tick recomposes only this text, not the QR. */
@Composable
private fun ExpiresInText(secondsLeft: () -> Long) {
    Text(stringResource(R.string.promptpay_expires_in, formatCountdown(secondsLeft())), style = MaterialTheme.typography.titleLarge)
}

private fun formatCountdown(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
