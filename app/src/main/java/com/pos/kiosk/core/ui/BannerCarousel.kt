package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import coil3.compose.AsyncImage
import com.pos.kiosk.domain.model.DisplayImage
import kotlinx.coroutines.delay

/** Banner frame shape on every device, so a 3:1 upload (1800×600) is never cropped. */
const val BANNER_ASPECT_RATIO = 3f

/** A pager this long behaves as endless in both directions without wrap-around jumps. */
private const val LOOP_PAGES = 10_000

/**
 * Menu-page promotions from pos-web /kiosk-settings, pinned above the categories on portrait screens (landscape
 * kiosks show none): swipeable, advancing every [slideSeconds] when nobody is dragging it. One image is shown still.
 */
@Composable
fun BannerCarousel(images: List<DisplayImage>, slideSeconds: Int, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    val looping = images.size > 1
    val pageCount = if (looping) LOOP_PAGES else 1
    // Start mid-way, on a page that maps to the first image.
    val startPage = if (looping) (LOOP_PAGES / 2).let { it - it % images.size } else 0
    val pager = rememberPagerState(initialPage = startPage) { pageCount }
    val dragged by pager.interactionSource.collectIsDraggedAsState()

    if (looping) {
        LaunchedEffect(pager.settledPage, dragged, slideSeconds) {
            if (dragged) return@LaunchedEffect
            delay(slideSeconds * 1_000L)
            pager.animateScrollToPage(pager.settledPage + 1)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(BANNER_ASPECT_RATIO)
            .clip(KioskCardShape)
            .background(MaterialTheme.colorScheme.primary.tint(0.12f))
            .testTag("banner_carousel"),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { page -> "${images[page % images.size].id}#$page" }) { page ->
            AsyncImage(
                model = images[page % images.size].url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (looping) {
            PageDots(
                count = images.size,
                index = pager.currentPage % images.size,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}
