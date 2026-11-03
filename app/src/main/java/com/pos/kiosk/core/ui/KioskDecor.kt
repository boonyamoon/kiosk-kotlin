package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** The brand colour mixed into white — the soft pastel the ordering screens sit on. */
fun Color.tint(alpha: Float): Color = copy(alpha = alpha).compositeOver(Color.White)

/**
 * Pastel brand-tinted backdrop with a few soft "petal" blobs in the corners. Everything is derived
 * from the theme's primary colour, so it follows the branch's `theme_color`.
 */
@Composable
fun SoftBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(primary.tint(0.10f), Color.White, primary.tint(0.07f))))
            .drawBehind {
                val blob = primary.copy(alpha = 0.10f)
                drawCircle(blob, radius = size.minDimension * 0.28f, center = Offset(-size.width * 0.02f, size.height * 0.30f))
                drawCircle(blob, radius = size.minDimension * 0.22f, center = Offset(size.width * 1.0f, size.height * 0.88f))
                drawCircle(blob, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.04f, size.height * 1.0f))
            },
        content = content,
    )
}
