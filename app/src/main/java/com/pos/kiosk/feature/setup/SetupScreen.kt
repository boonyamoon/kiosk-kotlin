package com.pos.kiosk.feature.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.FormErrorLine
import com.pos.kiosk.core.ui.GradientButton
import com.pos.kiosk.core.ui.MeedPasswordField
import com.pos.kiosk.core.ui.MeedTextField
import com.pos.kiosk.core.ui.ObserveAsEvents
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.messageRes
import com.pos.kiosk.core.ui.theme.MeedColors
import com.pos.kiosk.domain.model.KioskBranch

@Composable
fun SetupRoute(onPaired: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect -> if (effect == SetupEffect.Paired) onPaired() }
    SetupScreen(state, viewModel::onEvent)
}

@Composable
fun SetupScreen(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        SetupBackground(Modifier.matchParentSize())
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val sideBySide = maxWidth > maxHeight && maxWidth >= 700.dp
            val screenHeight = maxHeight
            when {
                state.step == SetupStep.NO_BUSINESS || state.step == SetupStep.NO_KIOSK_PERMISSION -> NoAccessCard(state.step, onEvent)
                state.step != SetupStep.LOGIN -> StepCard(state, onEvent)
                sideBySide -> Row(
                    Modifier.fillMaxSize().padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BrandHeader()
                        Spacer(Modifier.height(16.dp))
                        StoreIllustration(Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(400f / 220f))
                    }
                    Column(
                        Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    ) {
                        LoginForm(state, onEvent)
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier.heightIn(min = screenHeight).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BrandHeader()
                        Spacer(Modifier.height(24.dp))
                        StoreIllustration(Modifier.fillMaxWidth().aspectRatio(400f / 220f))
                        Spacer(Modifier.height(32.dp))
                        LoginForm(state, onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(logoSize: TextUnit = 64.sp) {
    Row {
        Text(
            stringResource(R.string.brand_meed),
            color = MeedColors.Orange,
            fontSize = logoSize,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.brand_kiosk),
            color = MeedColors.Orange,
            fontSize = logoSize * 0.45f,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.setup_tagline), color = MeedColors.Text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun LoginForm(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MeedTextField(
            value = state.email,
            onValueChange = { onEvent(SetupEvent.EmailChanged(it)) },
            placeholder = stringResource(R.string.setup_email_placeholder),
            leadingIcon = KioskIcons.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        )
        MeedPasswordField(
            value = state.password,
            onValueChange = { onEvent(SetupEvent.PasswordChanged(it)) },
            placeholder = stringResource(R.string.setup_password),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onEvent(SetupEvent.Login)
            }),
        )
        ErrorLine(state)
        Spacer(Modifier.height(4.dp))
        // Always looks active (as in the design); pressing it with empty fields shows a hint instead.
        GradientButton(
            text = stringResource(R.string.setup_login),
            onClick = {
                focusManager.clearFocus()
                onEvent(SetupEvent.Login)
            },
            loading = state.loading,
        )
    }
}

@Composable
private fun StepCard(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandHeader(logoSize = 44.sp)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircleBackButton { onEvent(SetupEvent.Back) }
                val isBranchStep = state.step == SetupStep.BRANCH
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (isBranchStep) R.string.setup_choose_branch else R.string.setup_choose_terminal),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        if (isBranchStep) stringResource(R.string.setup_choose_branch_subtitle) else state.selectedBranch?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MeedColors.Text,
                    )
                }
                if (isBranchStep) BranchStep(state, onEvent) else TerminalStep(state, onEvent)
                ErrorLine(state)
            }
        }
    }
}

/** Signed in but nothing to set up: no store at all, or a store without kiosk permission. Mirrors myapp's no-business screen. */
@Composable
private fun NoAccessCard(step: SetupStep, onEvent: (SetupEvent) -> Unit) {
    val noBusiness = step == SetupStep.NO_BUSINESS
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandHeader(logoSize = 44.sp)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(104.dp).background(MeedColors.PeachSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        if (noBusiness) KioskIcons.Storefront else KioskIcons.Lock,
                        contentDescription = null,
                        tint = MeedColors.Orange,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Text(
                    stringResource(if (noBusiness) R.string.no_business_title else R.string.no_kiosk_permission_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (noBusiness) R.string.no_business_description else R.string.no_kiosk_permission_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MeedColors.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    text = stringResource(R.string.setup_sign_out),
                    onClick = { onEvent(SetupEvent.SignOut) },
                    icon = KioskIcons.Logout,
                )
            }
        }
    }
}

@Composable
private fun CircleBackButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(MeedColors.Peach, CircleShape), contentAlignment = Alignment.Center) {
            Icon(KioskIcons.ArrowBack, contentDescription = null, tint = MeedColors.Orange)
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.action_back), style = MaterialTheme.typography.titleMedium, color = MeedColors.Text)
    }
}

@Composable
private fun BranchStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    // Plain Column: a staff account has a handful of branches and the whole screen already scrolls.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.branches.forEach { branch ->
            BranchCard(branch, selected = state.selectedBranch?.id == branch.id, enabled = !state.loading) {
                onEvent(SetupEvent.SelectBranch(branch))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    GradientButton(
        stringResource(R.string.setup_continue),
        { onEvent(SetupEvent.ConfirmBranch) },
        enabled = state.selectedBranch != null,
        loading = state.loading,
    )
}

@Composable
private fun BranchCard(branch: KioskBranch, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MeedColors.Peach.copy(alpha = 0.55f) else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) MeedColors.Orange else MeedColors.Border, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("branch_option")
            .padding(12.dp),
    ) {
        // Photo takes about a third of the card, so long branch names still fit on a phone.
        val thumbWidth = (maxWidth * 0.32f).coerceIn(84.dp, 150.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BranchThumbnail(branch.imageUrl, Modifier.size(width = thumbWidth, height = thumbWidth * (2f / 3f)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                branch.businessName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MeedColors.Placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (selected) {
                Box(Modifier.size(38.dp).background(MeedColors.Orange, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(KioskIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                Box(Modifier.size(34.dp).border(2.dp, MeedColors.Placeholder, CircleShape))
            }
        }
    }
}

/** Branch photo (or store logo); the MeeD icon while loading, on error, or when there is none. */
@Composable
private fun BranchThumbnail(imageUrl: String?, modifier: Modifier) {
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(MeedColors.PeachSoft), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_meed_logo), contentDescription = null, modifier = Modifier.fillMaxSize(0.6f))
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        if (imageUrl == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TerminalStep(state: SetupUiState, onEvent: (SetupEvent) -> Unit) {
    if (state.terminals.isEmpty()) {
        Text(stringResource(R.string.setup_no_terminal), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
    TerminalOption(stringResource(R.string.setup_terminal_auto), null, state.selectedTerminalId == null) { onEvent(SetupEvent.SelectTerminal(null)) }
    state.terminals.forEach { terminal ->
        val subtitle = terminal.pairedDeviceName?.takeIf { terminal.isPaired }?.let { stringResource(R.string.setup_terminal_paired, it) }
        TerminalOption("${terminal.name} (${terminal.code})", subtitle, state.selectedTerminalId == terminal.id) {
            onEvent(SetupEvent.SelectTerminal(terminal.id))
        }
    }
    MeedTextField(
        value = state.deviceName,
        onValueChange = { onEvent(SetupEvent.DeviceNameChanged(it)) },
        placeholder = stringResource(R.string.setup_device_name),
        leadingIcon = KioskIcons.TabletAndroid,
        label = stringResource(R.string.setup_device_name),
    )
    Spacer(Modifier.height(4.dp))
    GradientButton(stringResource(R.string.setup_pair), { onEvent(SetupEvent.Pair) }, enabled = state.canPair, loading = state.loading)
}

@Composable
private fun TerminalOption(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MeedColors.Orange))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MeedColors.Placeholder) }
        }
    }
}

@Composable
private fun ErrorLine(state: SetupUiState) {
    val message = when {
        state.error != null -> stringResource(state.error.messageRes())
        state.missingCredentials -> stringResource(R.string.setup_fill_credentials)
        else -> return
    }
    FormErrorLine(message)
}
