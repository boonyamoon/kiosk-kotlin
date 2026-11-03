package com.pos.kiosk.feature.success

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatBahtExact
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.ui.icons.KioskIcons

@Composable
fun SuccessRoute(onDone: () -> Unit, viewModel: SuccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.done) { onDone() }
    BackHandler(enabled = true) { viewModel.finish() }
    SuccessScreen(state, { secondsLeft }, viewModel::printAgain, viewModel::finish)
}

@Composable
fun SuccessScreen(state: SuccessUiState, secondsLeft: () -> Int, onPrintAgain: () -> Unit, onDone: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        // Short landscape screens: queue number on the left, payment/print info on the right,
        // so "pay at the counter" is never pushed below the fold.
        if (maxHeight < 760.dp && maxWidth > maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { QueueBlock(state) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusBlock(state, secondsLeft, onPrintAgain, onDone)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 720.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                QueueBlock(state)
                StatusBlock(state, secondsLeft, onPrintAgain, onDone)
            }
        }
    }
}

@Composable
private fun ColumnScope.QueueBlock(state: SuccessUiState) {
    Icon(KioskIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(96.dp))
    Text(
        stringResource(if (state.isPaid) R.string.success_title_paid else R.string.success_title_unpaid),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
    )
    state.queueNumber?.let {
        Text(stringResource(R.string.success_queue), style = MaterialTheme.typography.titleLarge)
        Text(it, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ColumnScope.StatusBlock(state: SuccessUiState, secondsLeft: () -> Int, onPrintAgain: () -> Unit, onDone: () -> Unit) {
    if (!state.isPaid) {
        Text(
            stringResource(R.string.success_pay_at_counter, formatBahtExact(state.total)),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    val printMessage = when (state.printStatus) {
        PrintStatus.PRINTING -> R.string.success_printing
        PrintStatus.PRINTED -> null
        PrintStatus.FAILED -> R.string.success_print_failed
        PrintStatus.NO_PRINTER -> R.string.success_no_printer
    }
    printMessage?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (state.printStatus == PrintStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (state.printStatus == PrintStatus.FAILED) {
        KioskOutlinedButton(stringResource(R.string.success_print_again), onPrintAgain, Modifier.fillMaxWidth())
    }
    KioskButton(stringResource(R.string.success_done), onDone, Modifier.fillMaxWidth())
    BackToStartText(secondsLeft)
}

/** Reads the countdown itself (deferred read), so each tick recomposes only this text. */
@Composable
private fun BackToStartText(secondsLeft: () -> Int) {
    Text(stringResource(R.string.success_back_in, secondsLeft()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
}
