package com.pos.kiosk.feature.attract

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.PageDots
import com.pos.kiosk.domain.model.DisplayImage
import com.pos.kiosk.domain.model.KioskDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/** How a full-screen image is drawn: cropped to fill, or whole on a blurred copy of itself. */
internal enum class FitMode { Fill, Letterbox }

/** Crop only when the image's shape is within this fraction of the screen's (same rule as the pos-web preview). */
internal const val FILL_TOLERANCE = 0.15f

internal fun fitMode(imageAspect: Float, screenAspect: Float): FitMode =
    if (abs(imageAspect - screenAspect) / screenAspect <= FILL_TOLERANCE) FitMode.Fill else FitMode.Letterbox

@Stable
internal class ScreensaverTrigger {
    var visible by mutableStateOf(false)
        internal set

    fun dismiss() {
        visible = false
    }
}

/**
 * Shows the screensaver after [KioskDisplay.Screensaver.idleSeconds] without a touch on the first screen.
 * Any touch (MainActivity reports every ACTION_DOWN to [lastInteraction]) hides it and restarts the wait.
 * The wait counts from when this screen appeared at the latest, so returning home after an abandoned order
 * shows the first screen first, not the screensaver straight away. Runs only while the app is resumed.
 */
@Composable
internal fun rememberScreensaverTrigger(config: KioskDisplay.Screensaver?, lastInteraction: StateFlow<Long>): ScreensaverTrigger {
    val trigger = remember { ScreensaverTrigger() }
    val shownAt = remember { SystemClock.elapsedRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = config?.active == true
    val idleMs = (config?.idleSeconds ?: 0) * 1_000L

    LaunchedEffect(active, idleMs, lastInteraction, lifecycle) {
        trigger.visible = false
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            lastInteraction.collectLatest { last ->
                trigger.visible = false
                val idleSince = maxOf(last, shownAt)
                delay((idleMs - (SystemClock.elapsedRealtime() - idleSince)).coerceAtLeast(0))
                trigger.visible = true
            }
        }
    }
    return trigger
}

/**
 * Full-screen slideshow over the first screen. A tap anywhere dismisses it (and is consumed, so it never
 * presses a button underneath). [onLoop] runs at the start of every pass through the images.
 */
@Composable
internal fun Screensaver(visible: Boolean, config: KioskDisplay.Screensaver, onDismiss: () -> Unit, onLoop: () -> Unit) {
    AnimatedVisibility(
        visible = visible && config.images.isNotEmpty(),
        enter = fadeIn(tween(durationMillis = 700)),
        exit = fadeOut(tween(durationMillis = 250)),
    ) {
        val images = config.images
        val currentOnLoop by rememberUpdatedState(onLoop)
        var index by remember { mutableIntStateOf(0) }

        LaunchedEffect(images, config.slideSeconds) {
            index = 0
            while (true) {
                if (index == 0) currentOnLoop()
                delay(config.slideSeconds * 1_000L)
                index = (index + 1) % images.size
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag("screensaver"),
        ) {
            val current = images[index.coerceIn(0, images.lastIndex)]
            Crossfade(targetState = current, animationSpec = tween(durationMillis = 900), label = "screensaverSlide") { image: DisplayImage ->
                FullScreenImage(image.url)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TouchToStartPill()
                if (images.size > 1) PageDots(count = images.size, index = index)
            }
        }
    }
}

/** Crops to fill when the shapes are close; otherwise shows the whole image on a blurred, darkened copy. */
@Composable
internal fun FullScreenImage(url: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val screenAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
        var imageAspect by remember(url) { mutableFloatStateOf(0f) }
        val mode = if (imageAspect > 0f) fitMode(imageAspect, screenAspect) else FitMode.Fill

        if (mode == FitMode.Letterbox) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Blur needs Android 12+; on older tablets the dark veil alone keeps the backdrop quiet.
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }.blur(28.dp, BlurredEdgeTreatment.Unbounded),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = if (mode == FitMode.Fill) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.isSpecified && size.height > 0f) imageAspect = size.width / size.height
            },
        )
    }
}

@Composable
private fun TouchToStartPill() {
    val pulse by rememberInfiniteTransition(label = "touchPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "touchPulseScale",
    )
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
    ) {
        Text(
            stringResource(R.string.screensaver_touch_to_start),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F1F1F),
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 16.dp),
        )
    }
}
