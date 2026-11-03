package com.pos.kiosk.feature.setup

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pos.kiosk.core.ui.theme.MeedColors
import kotlin.math.min

/** Orange waves (top-left, bottom-right) and faint café decorations, sized off the shorter screen edge. */
@Composable
internal fun SetupBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = min(w, h)
        val u = density // 1dp in px

        drawPath(
            Path().apply {
                moveTo(0f, h - s * 0.40f)
                cubicTo(s * 0.25f, h - s * 0.42f, s * 0.40f, h - s * 0.20f, s * 0.65f, h)
                lineTo(0f, h)
                close()
            },
            MeedColors.PeachSoft,
        )
        drawCafe(Offset(0f, h), u)
        drawBuilding(Offset(w, h - s * 0.04f), u)

        // Top-left: translucent outer wave, then the solid gradient wave.
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(s * 0.70f, 0f)
                cubicTo(s * 0.58f, s * 0.10f, s * 0.40f, s * 0.10f, s * 0.25f, s * 0.20f)
                cubicTo(s * 0.15f, s * 0.27f, s * 0.07f, s * 0.29f, 0f, s * 0.30f)
                close()
            },
            MeedColors.OrangeSoft.copy(alpha = 0.45f),
        )
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(s * 0.55f, 0f)
                cubicTo(s * 0.45f, s * 0.06f, s * 0.32f, s * 0.05f, s * 0.20f, s * 0.13f)
                cubicTo(s * 0.12f, s * 0.19f, s * 0.06f, s * 0.22f, 0f, s * 0.24f)
                close()
            },
            Brush.linearGradient(listOf(MeedColors.OrangeLight, MeedColors.Orange), Offset.Zero, Offset(s * 0.55f, s * 0.24f)),
        )

        // Bottom-right.
        drawPath(
            Path().apply {
                moveTo(w, h - s * 0.30f)
                cubicTo(w - s * 0.12f, h - s * 0.24f, w - s * 0.30f, h - s * 0.12f, w - s * 0.70f, h)
                lineTo(w, h)
                close()
            },
            MeedColors.OrangeSoft.copy(alpha = 0.45f),
        )
        drawPath(
            Path().apply {
                moveTo(w, h - s * 0.22f)
                cubicTo(w - s * 0.10f, h - s * 0.17f, w - s * 0.25f, h - s * 0.06f, w - s * 0.55f, h)
                lineTo(w, h)
                close()
            },
            Brush.linearGradient(listOf(MeedColors.OrangeLight, MeedColors.Orange), Offset(w, h - s * 0.22f), Offset(w - s * 0.55f, h)),
        )
    }
}

private fun DrawScope.drawCafe(bottomLeft: Offset, u: Float) {
    val color = MeedColors.Peach
    val x = bottomLeft.x
    val y = bottomLeft.y
    // Plant: stem + leaves.
    val stem = Offset(x + 46 * u, y - 150 * u)
    drawLine(Color(0xFFEAD9A8), stem, Offset(stem.x, y - 92 * u), strokeWidth = 3 * u)
    listOf(-50f, -20f, 15f, 45f, -35f, 30f).forEachIndexed { i, angle ->
        val leafCenter = Offset(stem.x + (if (i % 2 == 0) -12 else 12) * u, stem.y + (i * 9) * u)
        rotate(angle, leafCenter) {
            drawOval(Color(0xFFEFE0B5), Offset(leafCenter.x - 7 * u, leafCenter.y - 15 * u), Size(14 * u, 30 * u))
        }
    }
    drawRoundRect(color, Offset(x + 36 * u, y - 96 * u), Size(22 * u, 18 * u), CornerRadius(3 * u))
    // Table.
    drawRoundRect(color, Offset(x + 60 * u, y - 78 * u), Size(120 * u, 8 * u), CornerRadius(4 * u))
    drawRect(color, Offset(x + 116 * u, y - 70 * u), Size(8 * u, 62 * u))
    drawRoundRect(color, Offset(x + 96 * u, y - 10 * u), Size(48 * u, 6 * u), CornerRadius(3 * u))
    // Chairs either side.
    listOf(x + 22 * u, x + 196 * u).forEach { chairX ->
        drawRoundRect(color, Offset(chairX, y - 104 * u), Size(8 * u, 100 * u), CornerRadius(4 * u))
        drawRoundRect(color, Offset(chairX - 10 * u, y - 52 * u), Size(34 * u, 7 * u), CornerRadius(3 * u))
        drawRect(color, Offset(chairX + 18 * u, y - 46 * u), Size(5 * u, 42 * u))
    }
}

private fun DrawScope.drawBuilding(bottomRight: Offset, u: Float) {
    val color = MeedColors.Peach.copy(alpha = 0.6f)
    val left = bottomRight.x - 96 * u
    val top = bottomRight.y - 150 * u
    drawRect(color, Offset(left, top), Size(96 * u, 150 * u))
    for (row in 0 until 4) {
        for (col in 0 until 3) {
            drawRect(
                Color.White.copy(alpha = 0.7f),
                Offset(left + 12 * u + col * 26 * u, top + 16 * u + row * 32 * u),
                Size(16 * u, 18 * u),
            )
        }
    }
}

/** Shop with a striped awning next to a kiosk terminal — drawn in a 400×220 design space, scaled to fit. */
@Composable
internal fun StoreIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = min(size.width / 400f, size.height / 220f)
        val dx = (size.width - 400f * scale) / 2f
        val dy = (size.height - 220f * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawStoreScene()
        }
    }
}

private fun DrawScope.drawStoreScene() {
    val cloud = Color(0xFFFFE9DC)
    val tree = Color(0xFFF9D2B6)
    val trunk = Color(0xFFF3BE98)
    val awningOrange = Color(0xFFFF6A13)
    val awningLight = Color(0xFFFFE6D4)

    // Clouds.
    listOf(Offset(40f, 150f), Offset(282f, 146f)).forEach { o ->
        drawRoundRect(cloud, Offset(o.x, o.y), Size(120f, 44f), CornerRadius(22f))
        drawCircle(cloud, 26f, Offset(o.x + 38f, o.y + 2f))
        drawCircle(cloud, 32f, Offset(o.x + 72f, o.y - 4f))
    }
    // Trees.
    listOf(78f, 330f).forEach { x ->
        drawRect(trunk, Offset(x - 2f, 160f), Size(4f, 40f))
        drawOval(tree, Offset(x - 15f, 122f), Size(30f, 46f))
        drawLine(trunk, Offset(x, 150f), Offset(x - 7f, 142f), strokeWidth = 2f)
        drawLine(trunk, Offset(x, 154f), Offset(x + 7f, 146f), strokeWidth = 2f)
    }
    // Ground.
    drawLine(MeedColors.Peach, Offset(20f, 200f), Offset(380f, 200f), strokeWidth = 3f, cap = StrokeCap.Round)

    // Shop body.
    drawRect(Color(0xFFFFF8F3), Offset(124f, 92f), Size(124f, 108f))
    drawRect(MeedColors.Peach, Offset(124f, 92f), Size(124f, 108f), style = Stroke(width = 2f))
    // Awning: rounded cap, stripes, scalloped edge.
    val stripes = 6
    val awningLeft = 114f
    val awningWidth = 144f
    val stripeWidth = awningWidth / stripes
    drawRoundRect(awningOrange, Offset(awningLeft + 4f, 52f), Size(awningWidth - 8f, 10f), CornerRadius(5f))
    for (i in 0 until stripes) {
        val color = if (i % 2 == 0) awningOrange else awningLight
        val left = awningLeft + i * stripeWidth
        drawRect(color, Offset(left, 60f), Size(stripeWidth, 26f))
        drawArc(color, 0f, 180f, true, Offset(left, 86f - stripeWidth / 2f), Size(stripeWidth, stripeWidth))
    }
    // Door.
    drawRoundRect(awningOrange, Offset(140f, 138f), Size(28f, 62f), CornerRadius(2f))
    drawCircle(Color.White, 2.5f, Offset(162f, 172f))
    // Window with shine.
    drawRect(MeedColors.Peach, Offset(184f, 128f), Size(50f, 34f))
    drawRect(awningOrange, Offset(184f, 128f), Size(50f, 34f), style = Stroke(width = 5f))
    drawLine(Color.White.copy(alpha = 0.85f), Offset(200f, 156f), Offset(214f, 134f), strokeWidth = 3f)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(210f, 156f), Offset(224f, 134f), strokeWidth = 3f)

    // Kiosk terminal: base, neck, and a screen leaning back (drawn as a skewed quad for depth).
    drawRoundRect(Color(0xFF9A4A1F), Offset(274f, 190f), Size(60f, 10f), CornerRadius(5f))
    drawPath(
        Path().apply {
            moveTo(296f, 192f); lineTo(310f, 192f); lineTo(316f, 146f); lineTo(302f, 146f); close()
        },
        Color(0xFF3A3A3A),
    )
    drawPath(quad(Offset(256f, 104f), Offset(326f, 86f), Offset(346f, 140f), Offset(276f, 158f)), Color(0xFF2F2F2F))
    drawPath(quad(Offset(262f, 106f), Offset(322f, 91f), Offset(338f, 136f), Offset(279f, 151f)), awningOrange)
    drawPath(quad(Offset(262f, 106f), Offset(322f, 91f), Offset(326f, 102f), Offset(266f, 117f)), Color.White.copy(alpha = 0.18f))
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
}
