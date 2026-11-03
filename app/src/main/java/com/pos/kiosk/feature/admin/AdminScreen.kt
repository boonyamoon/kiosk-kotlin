package com.pos.kiosk.feature.admin

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.session.LockMode
import com.pos.kiosk.core.session.LockTaskState
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.FormErrorLine
import com.pos.kiosk.core.ui.GradientButton
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCard
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskConfirmDialog
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.KioskMutedSurface
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.ui.PhoneNumberPad
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.messageRes
import com.pos.kiosk.core.ui.theme.KioskTheme
import com.pos.kiosk.core.ui.theme.MeedColors

@Composable
fun AdminRoute(onClose: () -> Unit, onUnpaired: () -> Unit, onOpenLockSetup: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    ObserveAsEvents(viewModel.effects) { if (it == AdminEffect.Unpaired) onUnpaired() }
    AdminScreen(
        state = state,
        onEvent = viewModel::onEvent,
        lockActions = LockActions(
            onExit = { activity?.let(viewModel::exitLockTask) },
            onLockNow = { activity?.let(viewModel::lockNow) },
            onReleaseDeviceOwner = { activity?.let(viewModel::releaseDeviceOwner) },
            onOpenSetup = onOpenLockSetup,
        ),
        onClose = onClose,
    )
}

/** What the lock section's buttons do; they need the Activity, which only the route has. */
class LockActions(
    val onExit: () -> Unit,
    val onLockNow: () -> Unit,
    val onReleaseDeviceOwner: () -> Unit,
    val onOpenSetup: () -> Unit,
)

@Composable
fun AdminScreen(state: AdminUiState, onEvent: (AdminEvent) -> Unit, lockActions: LockActions, onClose: () -> Unit) {
    var confirmUnpair by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().imePadding().padding(32.dp), contentAlignment = Alignment.Center) {
        KioskCard(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BackCircleButton(onClose)
                    Text(stringResource(R.string.admin_title), style = MaterialTheme.typography.headlineMedium)
                }
                if (!state.authenticated) {
                    PinForm(state, onEvent)
                } else {
                    state.staffName?.let { Text(stringResource(R.string.admin_unlocked_by, it), style = MaterialTheme.typography.bodyLarge, color = MeedColors.Text) }
                    state.config?.let { config ->
                        Text(stringResource(R.string.admin_terminal_info, config.terminalName, config.branch.branchName), style = MaterialTheme.typography.titleMedium)
                        Text(
                            config.receiptPrinter?.let { stringResource(R.string.admin_printer_info, it.name, "${it.host}:${it.port}") }
                                ?: stringResource(R.string.admin_printer_none),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    KioskButton(stringResource(R.string.admin_test_print), { onEvent(AdminEvent.TestPrint) }, Modifier.fillMaxWidth(), loading = state.busy)
                    KioskOutlinedButton(stringResource(R.string.admin_refresh), { onEvent(AdminEvent.Refresh) }, Modifier.fillMaxWidth(), enabled = !state.busy)
                    LockTaskSection(
                        lock = state.lock,
                        onExit = lockActions.onExit,
                        onLockNow = lockActions.onLockNow,
                        onOpenSetup = lockActions.onOpenSetup,
                        onRelease = { confirmRelease = true },
                    )
                    KioskOutlinedButton(stringResource(R.string.admin_unpair), { confirmUnpair = true }, Modifier.fillMaxWidth(), enabled = !state.busy)
                    state.messageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.secondary) }
                    state.error?.let { FormErrorLine(stringResource(it.messageRes())) }
                }
            }
        }
    }

    if (confirmRelease) {
        KioskConfirmDialog(
            title = stringResource(R.string.admin_release_title),
            message = stringResource(R.string.admin_release_message),
            confirmText = stringResource(R.string.admin_release_confirm),
            onConfirm = {
                confirmRelease = false
                lockActions.onReleaseDeviceOwner()
            },
            onDismiss = { confirmRelease = false },
            icon = KioskIcons.Lock,
            destructive = true,
        )
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            text = { Text(stringResource(R.string.admin_unpair_confirm), style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    onEvent(AdminEvent.Unpair)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Staff PIN on an on-screen pad (no system keyboard on a locked kiosk); dots only, never the digits. */
@Composable
private fun ColumnScope.PinForm(state: AdminUiState, onEvent: (AdminEvent) -> Unit) {
    Text(stringResource(R.string.admin_pin_subtitle), style = MaterialTheme.typography.bodyLarge, color = MeedColors.Text)
    PinDots(state.pin.length, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
    state.error?.let { FormErrorLine(stringResource(it.messageRes())) }
    PhoneNumberPad(
        onDigit = { onEvent(AdminEvent.PinDigit(it)) },
        onBackspace = { onEvent(AdminEvent.PinBackspace) },
        modifier = Modifier.fillMaxWidth(),
        keyHeight = 72.dp,
    )
    Spacer(Modifier.height(4.dp))
    GradientButton(
        stringResource(R.string.admin_unlock),
        { onEvent(AdminEvent.SubmitPin) },
        enabled = state.canSubmitPin,
        loading = state.busy,
        icon = KioskIcons.Lock,
    )
}

/** One dot per digit typed, never fewer than four slots so the expected length reads at a glance. */
@Composable
private fun PinDots(length: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.admin_pin_entered, length, length)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(maxOf(ADMIN_PIN_MIN_LENGTH, length)) { index ->
            val filled = index < length
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(2.dp, if (filled) MaterialTheme.colorScheme.primary else KioskHairline, CircleShape),
            )
        }
    }
}

private val LockedGreen = Color(0xFF16A34A)
private val WarningAmber = Color(0xFFD97706)
private val OpenRed = Color(0xFFDC2626)

/** Status dot colour, headline and explanation for the lock section. */
private data class LockStatusText(val dot: Color, @StringRes val label: Int, @StringRes val detail: Int)

private fun LockTaskState.statusText(): LockStatusText = when (mode) {
    LockMode.Locked -> LockStatusText(LockedGreen, R.string.admin_lock_status_locked, R.string.admin_lock_detail_locked)
    LockMode.Pinned -> when {
        !deviceSecure -> LockStatusText(WarningAmber, R.string.admin_lock_status_pinned_unsafe, R.string.admin_lock_detail_no_screen_lock)
        pinExitLocked == false -> LockStatusText(WarningAmber, R.string.admin_lock_status_pinned_unsafe, R.string.admin_lock_detail_no_exit_pin)
        pinExitLocked == null -> LockStatusText(LockedGreen, R.string.admin_lock_status_pinned, R.string.admin_lock_detail_pinned_unknown)
        else -> LockStatusText(LockedGreen, R.string.admin_lock_status_pinned, R.string.admin_lock_detail_pinned)
    }
    LockMode.NotPinned -> LockStatusText(OpenRed, R.string.admin_lock_status_not_pinned, R.string.admin_lock_detail_not_pinned)
    LockMode.Suspended -> LockStatusText(WarningAmber, R.string.admin_lock_status_suspended, R.string.admin_lock_detail_suspended)
}

/** Setup the guide still lists as missing: no screen lock, the unpin PIN off, or another app as Home. */
private val LockTaskState.setupIncomplete: Boolean
    get() = !deviceOwner && (!deviceSecure || pinExitLocked == false || !defaultHome)

/**
 * Screen-lock status with only the buttons that apply: exit while locked or pinned, pin / lock again otherwise,
 * plus the setup guide. Giving up device owner is a quiet text button, behind a confirmation.
 */
@Composable
private fun LockTaskSection(
    lock: LockTaskState,
    onExit: () -> Unit,
    onLockNow: () -> Unit,
    onOpenSetup: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = lock.statusText()
    Column(
        modifier.fillMaxWidth().clip(KioskCardShape).background(KioskMutedSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.admin_lock_title), style = MaterialTheme.typography.titleMedium, color = KioskInk)
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(status.dot))
                Text(stringResource(status.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KioskInk)
            }
            Text(stringResource(status.detail), style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted)
        }
        when (lock.mode) {
            LockMode.Locked, LockMode.Pinned -> KioskOutlinedButton(stringResource(R.string.admin_exit_lock), onExit, Modifier.fillMaxWidth())
            LockMode.Suspended -> KioskButton(stringResource(R.string.admin_relock), onLockNow, Modifier.fillMaxWidth())
            LockMode.NotPinned -> KioskButton(stringResource(R.string.admin_pin_now), onLockNow, Modifier.fillMaxWidth())
        }
        KioskOutlinedButton(
            stringResource(if (lock.setupIncomplete) R.string.admin_lock_setup_incomplete else R.string.admin_lock_setup),
            onOpenSetup,
            Modifier.fillMaxWidth(),
        )
        if (lock.deviceOwner) {
            TextButton(onRelease, Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    stringResource(R.string.admin_release_device_owner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 560)
@Composable
private fun LockTaskSectionPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LockTaskSection(LockTaskState(LockMode.Pinned, deviceSecure = true, pinExitLocked = true, defaultHome = true), {}, {}, {}, {})
            LockTaskSection(LockTaskState(LockMode.Pinned, deviceSecure = false), {}, {}, {}, {})
            LockTaskSection(LockTaskState(LockMode.NotPinned), {}, {}, {}, {})
            LockTaskSection(LockTaskState(LockMode.Suspended, deviceOwner = true), {}, {}, {}, {})
        }
    }
}
