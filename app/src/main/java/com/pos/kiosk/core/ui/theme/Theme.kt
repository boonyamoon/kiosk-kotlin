package com.pos.kiosk.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt

val DefaultBrandColor = Color(0xFFFF5E00)

fun parseBrandColor(hex: String?): Color = try {
    if (hex.isNullOrBlank()) DefaultBrandColor else Color(hex.toColorInt())
} catch (_: IllegalArgumentException) {
    DefaultBrandColor
}

/** Kiosk screens are read at arm's length — everything one or two steps larger than phone defaults. */
private val KioskTypography = Typography(
    displayLarge = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun KioskTheme(brandColor: Color = DefaultBrandColor, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = brandColor,
            onPrimary = Color.White,
            primaryContainer = brandColor.copy(alpha = 0.12f),
            onPrimaryContainer = Color(0xFF1F1F1F),
            secondary = Color(0xFF2E7D32),
            onSecondary = Color.White,
            // Tonal buttons (quantity steppers etc.) follow the brand colour instead of the M3 default purple.
            secondaryContainer = brandColor.copy(alpha = 0.14f).compositeOver(Color.White),
            onSecondaryContainer = lerp(brandColor, Color.Black, 0.35f),
            background = Color(0xFFF7F7F7),
            surface = Color.White,
            surfaceVariant = Color(0xFFF0F0F0),
            error = Color(0xFFC62828),
        ),
        typography = KioskTypography,
        content = content,
    )
}
