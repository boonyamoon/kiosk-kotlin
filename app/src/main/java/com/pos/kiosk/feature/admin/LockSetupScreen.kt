package com.pos.kiosk.feature.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.session.LockMode
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.core.session.LockTaskState
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCard
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskHairline
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskInkMuted
import com.pos.kiosk.core.ui.KioskMutedSurface
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.KioskTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The four things that make app pinning hold, in the order staff should do them. */
enum class LockSetupStep { ScreenLock, UnpinPin, HomeApp, Pin }

enum class StepStatus { Done, Todo, Unknown }

fun LockTaskState.status(step: LockSetupStep): StepStatus = when (step) {
    LockSetupStep.ScreenLock -> if (deviceSecure) StepStatus.Done else StepStatus.Todo
    LockSetupStep.UnpinPin -> when (pinExitLocked) {
        true -> StepStatus.Done
        false -> StepStatus.Todo
        null -> StepStatus.Unknown
    }
    LockSetupStep.HomeApp -> if (defaultHome) StepStatus.Done else StepStatus.Todo
    LockSetupStep.Pin -> if (mode == LockMode.Pinned || mode == LockMode.Locked) StepStatus.Done else StepStatus.Todo
}

@HiltViewModel
class LockSetupViewModel @Inject constructor(private val lockTask: LockTaskController) : ViewModel() {
    val state: StateFlow<LockTaskState> = lockTask.state

    /** On every resume: staff may be coming back from Android settings with something changed. */
    fun refresh() = lockTask.refresh()

    fun pinNow(activity: Activity) = lockTask.requestLock(activity)

    /**
     * Android blocks other apps while the kiosk is pinned or locked, so this exits the lock first. The exit is the
     * usual temporary one: it ends when the kiosk is back on its first screen, and the app asks to pin again.
     */
    fun openSettings(activity: Activity, step: LockSetupStep) {
        lockTask.suspend(activity)
        val candidates = when (step) {
            LockSetupStep.ScreenLock -> listOf(DevicePolicyManager.ACTION_SET_NEW_PASSWORD, Settings.ACTION_SECURITY_SETTINGS)
            LockSetupStep.UnpinPin -> listOf(Settings.ACTION_SECURITY_SETTINGS)
            LockSetupStep.HomeApp -> listOf(Settings.ACTION_HOME_SETTINGS)
            LockSetupStep.Pin -> emptyList()
        } + Settings.ACTION_SETTINGS
        candidates.firstOrNull { action ->
            try {
                activity.startActivity(Intent(action))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

@Composable
fun LockSetupRoute(onClose: () -> Unit, viewModel: LockSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LockSetupScreen(
        state = state,
        onOpenSettings = { step -> activity?.let { viewModel.openSettings(it, step) } },
        onPinNow = { activity?.let(viewModel::pinNow) },
        onClose = onClose,
    )
}

/** Staff guide to make app pinning hold on a Play Store install: each step shows live whether it is done. */
@Composable
fun LockSetupScreen(state: LockTaskState, onOpenSettings: (LockSetupStep) -> Unit, onPinNow: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        KioskCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BackCircleButton(onClose)
                    Text(stringResource(R.string.lock_setup_title), style = MaterialTheme.typography.headlineMedium)
                }
                Text(stringResource(R.string.lock_setup_intro), style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted)
                if (state.deviceOwner) {
                    Text(
                        stringResource(R.string.lock_setup_device_owner),
                        style = MaterialTheme.typography.bodyLarge,
                        color = KioskInk,
                        modifier = Modifier.fillMaxWidth().clip(KioskCardShape).background(KioskMutedSurface).padding(16.dp),
                    )
                }
                LockSetupStep.entries.forEachIndexed { index, step ->
                    SetupStepRow(
                        number = index + 1,
                        step = step,
                        status = state.status(step),
                        onAction = { if (step == LockSetupStep.Pin) onPinNow() else onOpenSettings(step) },
                    )
                }
                Text(stringResource(R.string.lock_setup_footer), style = MaterialTheme.typography.bodyMedium, color = KioskInkMuted)
            }
        }
    }
}

private class StepText(@StringRes val title: Int, @StringRes val description: Int)

private val LockSetupStep.text: StepText
    get() = when (this) {
        LockSetupStep.ScreenLock -> StepText(R.string.lock_setup_step_screen_lock, R.string.lock_setup_step_screen_lock_detail)
        LockSetupStep.UnpinPin -> StepText(R.string.lock_setup_step_unpin_pin, R.string.lock_setup_step_unpin_pin_detail)
        LockSetupStep.HomeApp -> StepText(R.string.lock_setup_step_home, R.string.lock_setup_step_home_detail)
        LockSetupStep.Pin -> StepText(R.string.lock_setup_step_pin, R.string.lock_setup_step_pin_detail)
    }

private val DoneGreen = Color(0xFF16A34A)

@Composable
private fun SetupStepRow(number: Int, step: LockSetupStep, status: StepStatus, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val statusLabel = stringResource(
        when (status) {
            StepStatus.Done -> R.string.lock_setup_done
            StepStatus.Todo -> R.string.lock_setup_todo
            StepStatus.Unknown -> R.string.lock_setup_unknown
        },
    )
    Row(
        modifier.fillMaxWidth().clip(KioskCardShape).border(1.dp, KioskHairline, KioskCardShape).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepBadge(number, status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) { stateDescription = statusLabel }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(step.text.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KioskInk)
                Text(stringResource(step.text.description), style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted)
                if (status == StepStatus.Unknown) {
                    Text(stringResource(R.string.lock_setup_unknown_detail), style = MaterialTheme.typography.bodyMedium, color = KioskInkMuted)
                }
            }
            when {
                step == LockSetupStep.Pin && status != StepStatus.Done ->
                    KioskButton(stringResource(R.string.admin_pin_now), onAction, Modifier.fillMaxWidth())
                step != LockSetupStep.Pin && status != StepStatus.Done ->
                    KioskOutlinedButton(stringResource(R.string.lock_setup_open_settings), onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int, status: StepStatus) {
    val done = status == StepStatus.Done
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (done) DoneGreen else KioskMutedSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(KioskIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text(number.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KioskInk)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, widthDp = 800, heightDp = 1280)
@Composable
private fun LockSetupScreenPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) {
        LockSetupScreen(
            state = LockTaskState(mode = LockMode.NotPinned, deviceSecure = true, pinExitLocked = null, defaultHome = false),
            onOpenSettings = {},
            onPinNow = {},
            onClose = {},
        )
    }
}
