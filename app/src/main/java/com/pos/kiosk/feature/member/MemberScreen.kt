package com.pos.kiosk.feature.member

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.BackCircleButton
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.KioskCardShape
import com.pos.kiosk.core.ui.KioskInk
import com.pos.kiosk.core.ui.KioskOutlinedButton
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.ui.PhoneNumberPad
import com.pos.kiosk.core.ui.currentWindowWidth
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.messageRes
import com.pos.kiosk.core.ui.tint

@Composable
fun MemberRoute(onContinue: () -> Unit, onBack: () -> Unit, viewModel: MemberViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { if (it == MemberEffect.GoToMenu) onContinue() }
    MemberScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun MemberScreen(state: MemberUiState, onEvent: (MemberEvent) -> Unit, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(32.dp)) {
        // Short screens (landscape) put the keypad beside the number instead of below it.
        val sideBySide = maxHeight < 760.dp && maxWidth > maxHeight
        Box(Modifier.align(Alignment.TopStart)) { BackCircleButton(onBack) }
        if (sideBySide) {
            Row(
                Modifier.align(Alignment.Center).widthIn(max = 1100.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PhoneSummary(state, onEvent)
                    // With the keypad hidden (member found) the skip button stays under the summary.
                    if (state.member != null) SkipButton(state, onEvent)
                }
                if (state.member == null) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Keypad(state, onEvent, keyHeight = 64.dp)
                        SkipButton(state, onEvent)
                    }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PhoneSummary(state, onEvent)
                if (state.member == null) Keypad(state, onEvent, keyHeight = 88.dp)
                SkipButton(state, onEvent)
            }
        }
    }
}

@Composable
private fun PhoneSummary(state: MemberUiState, onEvent: (MemberEvent) -> Unit) {
    Text(stringResource(R.string.member_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = KioskInk)
    Text(stringResource(R.string.member_subtitle), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    // One line always: 56sp on tablets, 36sp on phone-width screens where 13 characters would not fit.
    val phoneStyle = if (currentWindowWidth() < 480.dp) {
        MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp)
    } else {
        MaterialTheme.typography.displayMedium
    }
    Text(
        formatPhone(state.phone).ifEmpty { "0XX-XXX-XXXX" },
        style = phoneStyle,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        color = if (state.phone.isEmpty()) MaterialTheme.colorScheme.outline else KioskInk,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
    )
    when {
        state.member != null -> MemberFoundCard(state, onEvent)
        state.notFound -> Text(stringResource(R.string.member_not_found), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        state.invalidPhone -> Text(stringResource(R.string.member_invalid_phone), color = MaterialTheme.colorScheme.error)
        state.error != null -> Text(stringResource(state.error.messageRes()), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SkipButton(state: MemberUiState, onEvent: (MemberEvent) -> Unit) {
    KioskOutlinedButton(
        stringResource(if (state.notFound || state.member != null) R.string.member_continue_guest else R.string.action_skip),
        { onEvent(MemberEvent.ContinueAsGuest) },
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Keypad(state: MemberUiState, onEvent: (MemberEvent) -> Unit, keyHeight: Dp) {
    PhoneNumberPad(
        onDigit = { onEvent(MemberEvent.Digit(it)) },
        onBackspace = { onEvent(MemberEvent.Backspace) },
        modifier = Modifier.fillMaxWidth(),
        keyHeight = keyHeight,
    )
    KioskButton(stringResource(R.string.member_check), { onEvent(MemberEvent.Check) }, Modifier.fillMaxWidth(), enabled = state.canCheck, loading = state.loading)
}

@Composable
private fun MemberFoundCard(state: MemberUiState, onEvent: (MemberEvent) -> Unit) {
    val member = state.member ?: return
    val primary = MaterialTheme.colorScheme.primary
    // Opaque white: the old translucent container let the shadow show through as a grey frame.
    Surface(Modifier.fillMaxWidth(), shape = KioskCardShape, color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).background(primary.tint(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(KioskIcons.Check, contentDescription = null, tint = primary, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.member_found, member.displayName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = KioskInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.background(primary.tint(0.12f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(KioskIcons.Star, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.member_points, member.availablePoints),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }
            Spacer(Modifier.height(24.dp))
            KioskButton(stringResource(R.string.member_continue), { onEvent(MemberEvent.ContinueAsMember) }, Modifier.fillMaxWidth())
        }
    }
}

private fun formatPhone(digits: String): String = when {
    digits.length <= 3 -> digits
    digits.length <= 6 -> "${digits.take(3)}-${digits.drop(3)}"
    else -> "${digits.take(3)}-${digits.substring(3, 6)}-${digits.drop(6)}"
}
