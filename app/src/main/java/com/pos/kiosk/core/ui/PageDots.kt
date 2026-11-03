package com.pos.kiosk.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Slide position for the screensaver and banner carousels: the current page is a wider pill. Meant for photos (white dots). */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == index
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "pageDotWidth")
            Box(Modifier.height(8.dp).width(width).background(if (selected) Color.White else Color.White.copy(alpha = 0.55f), CircleShape))
        }
    }
}
