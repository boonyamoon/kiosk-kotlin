package com.pos.kiosk.navigation

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.pos.kiosk.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

private const val IDLE_TIMEOUT_MS = 60_000L
private const val WARNING_SECONDS = 15

/**
 * After [IDLE_TIMEOUT_MS] without a touch, asks "still ordering?" for [WARNING_SECONDS] and then
 * calls [onTimeout]. Any touch (reported by MainActivity) restarts the timer.
 *
 * Touches are consumed as a flow inside the effect, not read as Compose state: a touch restarts the
 * timer coroutine but never recomposes this watcher or the nav host around it.
 */
@Composable
fun IdleWatcher(
    enabled: Boolean,
    lastInteraction: StateFlow<Long>,
    onInteraction: () -> Unit,
    onTimeout: () -> Unit,
    @StringRes titleRes: Int = R.string.idle_title,
    @StringRes continueRes: Int = R.string.idle_continue,
) {
    var warning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(WARNING_SECONDS) }
    val currentOnTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(enabled, lastInteraction) {
        warning = false
        if (!enabled) return@LaunchedEffect
        lastInteraction.collectLatest { last ->
            warning = false
            val idleFor = SystemClock.elapsedRealtime() - last
            delay((IDLE_TIMEOUT_MS - idleFor).coerceAtLeast(0))
            warning = true
            for (left in WARNING_SECONDS downTo 1) {
                secondsLeft = left
                delay(1_000)
            }
            warning = false
            currentOnTimeout()
        }
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(titleRes), style = MaterialTheme.typography.headlineMedium) },
            text = { Text(stringResource(R.string.idle_message, secondsLeft), style = MaterialTheme.typography.titleMedium) },
            // The dialog is its own window, so this tap never reaches MainActivity — report it here.
            confirmButton = { TextButton(onClick = onInteraction) { Text(stringResource(continueRes)) } },
            dismissButton = {
                TextButton(onClick = {
                    warning = false
                    onTimeout()
                }) { Text(stringResource(R.string.idle_restart)) }
            },
        )
    }
}
