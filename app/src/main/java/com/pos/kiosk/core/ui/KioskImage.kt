package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

// Light grey with a slightly darker grey icon, so it reads as "no photo yet" on any brand colour.
private val PlaceholderBackground = Color(0xFFF1F3F5)
private val PlaceholderIcon = Color(0xFFCDD1D6)

/**
 * Photo with a light-grey placeholder (faint grey icon) behind it. The placeholder is what shows while the image
 * loads, when it fails to load, and when the product has no image at all.
 *
 * The grey background stays under the photo (Coil cross-fades from it), but the icon is only drawn until the
 * photo has loaded, so loaded cards in the menu grid don't keep painting it underneath.
 */
@Composable
fun KioskImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier) {
    val hasImage = model != null && model != ""
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier.background(PlaceholderBackground), contentAlignment = Alignment.Center) {
        if (!loaded) ServingDome(PlaceholderIcon, Modifier.fillMaxSize(0.42f))
        if (hasImage) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * A steaming serving dome (cloche) on a tray, drawn on a unit square so it scales with its box.
 * The paths are built once per size in [drawWithCache], not on every draw pass (grid scrolling redraws often).
 */
@Composable
private fun ServingDome(color: Color, modifier: Modifier) {
    Spacer(
        modifier.aspectRatio(1f).drawWithCache {
            val s = size.minDimension
            val steamStroke = Stroke(width = s * 0.045f, cap = StrokeCap.Round)
            val steamColor = color.copy(alpha = color.alpha * 0.8f)
            // Dome: a half circle sitting on the tray.
            val dome = Path().apply {
                moveTo(s * 0.14f, s * 0.74f)
                arcTo(Rect(s * 0.14f, s * 0.74f - s * 0.36f, s * 0.86f, s * 0.74f + s * 0.36f), 180f, 180f, false)
                close()
            }
            // Three wavy steam lines above the knob.
            val steam = listOf(0.38f to 0.0f, 0.5f to 0.03f, 0.62f to 0.0f).map { (x, lift) ->
                Path().apply {
                    moveTo(s * x, s * 0.27f)
                    cubicTo(s * (x - 0.06f), s * 0.2f, s * (x + 0.06f), s * 0.14f, s * x, s * (0.04f + lift))
                }
            }
            onDrawBehind {
                // Tray, dome and knob.
                drawRoundRect(color, topLeft = Offset(s * 0.04f, s * 0.78f), size = Size(s * 0.92f, s * 0.06f), cornerRadius = CornerRadius(s * 0.03f))
                drawPath(dome, color)
                drawCircle(color, radius = s * 0.04f, center = Offset(s * 0.5f, s * 0.35f))
                steam.forEach { drawPath(it, steamColor, style = steamStroke) }
            }
        },
    )
}
