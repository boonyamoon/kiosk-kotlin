package com.pos.kiosk.feature.attract

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.ChoiceCard
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.tint
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.OrderType

@Composable
fun AttractRoute(
    onStart: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: AttractViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    // The kiosk is "fully drawn" once the branch config is on screen; lets startup benchmarks and Play vitals
    // report time-to-full-display instead of just the first (loading) frame.
    ReportDrawnWhen { state.config != null }
    BackHandler(enabled = true) { }
    val screensaverConfig = state.config?.display?.screensaver
    val screensaver = rememberScreensaverTrigger(screensaverConfig, viewModel.lastInteraction)
    Box(Modifier.fillMaxSize()) {
        AttractScreen(
            config = state.config,
            onStart = { type ->
                viewModel.startOrder(type)
                onStart()
            },
            onOpenAdmin = onOpenAdmin,
        )
        if (screensaverConfig != null) {
            Screensaver(
                visible = screensaver.visible,
                config = screensaverConfig,
                onDismiss = screensaver::dismiss,
                onLoop = viewModel::refreshIfStale,
            )
        }
    }
}

@Composable
fun AttractScreen(config: KioskConfig?, onStart: (OrderType) -> Unit, onOpenAdmin: () -> Unit) {
    var logoTaps by remember { mutableIntStateOf(0) }
    var firstTapAt by remember { mutableLongStateOf(0L) }

    // Hidden staff entry: five taps on the logo/name within 3 seconds.
    val adminTap = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        val now = System.currentTimeMillis()
        if (now - firstTapAt > 3_000) {
            firstTapAt = now
            logoTaps = 0
        }
        logoTaps++
        if (logoTaps >= 5) {
            logoTaps = 0
            onOpenAdmin()
        }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                LandscapeLayout(config, onStart, adminTap)
            } else {
                PortraitLayout(config, onStart, adminTap)
            }
        }
    }
}

/** Phone-like portrait kiosk: logo on top, product photo behind, ordering sheet at the bottom. */
@Composable
private fun PortraitLayout(config: KioskConfig?, onStart: (OrderType) -> Unit, adminTap: Modifier) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // The photo is only atmosphere here: blurred (Android 12+; older versions show it sharp) and
        // scaled up a little so the blur's soft edges fall outside the screen.
        config?.display?.backgroundImageUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(18.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        // A light veil over the whole photo keeps dark text readable anywhere on the screen.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primary.tint(0.08f).copy(alpha = 0.85f),
                    0.5f to Color.White.copy(alpha = 0.6f),
                    1f to Color.White.copy(alpha = 0.88f),
                ),
            ),
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandHeader(config, adminTap, Modifier.padding(top = 56.dp, start = 32.dp, end = 32.dp))
            Spacer(Modifier.weight(1f))
            LanguageRow(Modifier.padding(horizontal = 32.dp).padding(bottom = 28.dp))
        }
        // The ordering buttons sit in the middle of the screen, over the header's column rather than inside it.
        OrderChoices(config, onStart, Modifier.align(Alignment.Center).padding(horizontal = 32.dp).widthIn(max = 640.dp))
    }
}

/**
 * Tablet landscape. With a kiosk background (pos-web): the photo fills the screen and the branding + ordering
 * buttons sit on its lighter right half. Without one: the content centred on a soft wash of the brand colour.
 */
@Composable
private fun LandscapeLayout(config: KioskConfig?, onStart: (OrderType) -> Unit, adminTap: Modifier) {
    val background = config?.display?.backgroundImageUrl
    if (background != null) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = background, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Clear over the photo's left side, a soft white veil under the content so text stays readable.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0.3f to Color.Transparent,
                        0.55f to Color.White.copy(alpha = 0.82f),
                        1f to Color.White.copy(alpha = 0.9f),
                    ),
                ),
            )
            Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                LandscapeContent(config, onStart, Modifier.weight(1f), adminTap)
            }
        }
        return
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to MaterialTheme.colorScheme.primary.tint(0.14f),
                0.7f to Color.White,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        LandscapeContent(config, onStart, Modifier.widthIn(max = 720.dp), adminTap)
    }
}

@Composable
private fun LandscapeContent(config: KioskConfig?, onStart: (OrderType) -> Unit, modifier: Modifier, adminTap: Modifier) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandHeader(config, adminTap, Modifier)
        Spacer(Modifier.height(40.dp))
        OrderChoices(config, onStart, Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(32.dp))
        LanguageRow()
    }
}

@Composable
private fun BrandHeader(config: KioskConfig?, adminTap: Modifier, modifier: Modifier) {
    val branch = config?.branch
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (branch?.logoUrl != null) {
            AsyncImage(
                model = branch.logoUrl,
                contentDescription = branch.businessName,
                contentScale = ContentScale.Fit,
                modifier = adminTap.heightIn(max = 150.dp).widthIn(max = 360.dp),
            )
        } else {
            Text(
                branch?.businessName.orEmpty(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = adminTap,
            )
        }
        // Title (the branch's own message, or a default), a lighter helper line, and a short brand-colour rule.
        Spacer(Modifier.height(16.dp))
        Text(
            branch?.welcomeMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.attract_welcome),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1F1F1F),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(48.dp).height(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

@Composable
private fun OrderChoices(config: KioskConfig?, onStart: (OrderType) -> Unit, modifier: Modifier) {
    if (config != null && !config.isOpen) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.attract_closed), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            config.closedReason?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
        }
        return
    }
    val choices = buildList {
        if (config?.allowDineIn != false) {
            add(Triple(OrderType.DINE_IN, R.string.attract_dine_in to R.string.attract_dine_in_sub, KioskIcons.Restaurant))
        }
        if (config?.allowTakeaway == true) {
            add(Triple(OrderType.TAKE_AWAY, R.string.attract_take_away to R.string.attract_take_away_sub, KioskIcons.ShoppingBag))
        }
    }
    // Square cards side by side. A lone choice keeps the same card size, centred, instead of stretching.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (choices.size == 1) Spacer(Modifier.weight(0.5f))
        choices.forEachIndexed { index, (type, labels, icon) ->
            ChoiceCard(
                title = stringResource(labels.first),
                subtitle = stringResource(labels.second),
                icon = icon,
                filled = false,
                onClick = { onStart(type) },
                modifier = Modifier.weight(1f),
            )
        }
        if (choices.size == 1) Spacer(Modifier.weight(0.5f))
    }
}

@Composable
private fun LanguageRow(modifier: Modifier = Modifier) {
    val current = LocalConfiguration.current.locales[0].language
    val options = listOf("th" to R.string.language_th, "en" to R.string.language_en)
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: R.string.language_en
    var expanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary

    Row(modifier.widthIn(max = 640.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(KioskIcons.Language, contentDescription = null, tint = Color(0xFF2B2B2B), modifier = Modifier.size(24.dp))
        Text(
            stringResource(R.string.attract_language),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(0.01f))
        Box {
            Surface(
                onClick = { expanded = true },
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, primary.copy(alpha = 0.15f)),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(currentLabel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = primary)
                    Icon(KioskIcons.KeyboardArrowDown, contentDescription = null, tint = primary)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
                        onClick = {
                            expanded = false
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        },
                    )
                }
            }
        }
    }
}
