package com.pos.kiosk.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.KioskTheme

/**
 * The big square choice the customer taps: an icon disc, a title and a subtitle. The first choice of a row is
 * [filled] with the brand colour, the others are white — used for the order type (eat here / take away) and the
 * payment method. [loading] swaps the icon for a spinner while the choice is being acted on.
 */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val content = if (filled) Color.White else Color(0xFF2B2B2B)
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (filled) primary else Color.White,
        contentColor = content,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = if (filled) 8.dp else 4.dp,
        border = if (filled) null else BorderStroke(1.dp, primary.copy(alpha = 0.12f)),
        modifier = modifier.aspectRatio(1f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The icon disc scales with the card, so it looks right on a phone and on a tablet.
            Box(
                Modifier.fillMaxWidth(0.4f).aspectRatio(1f).background(if (filled) Color.White.copy(alpha = 0.2f) else primary.tint(0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = if (filled) Color.White else primary, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize(0.5f))
                } else {
                    Icon(icon, contentDescription = null, tint = if (filled) Color.White else primary, modifier = Modifier.fillMaxSize(0.55f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChoiceCardPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) {
        Row(Modifier.padding(16.dp).width(560.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard("เงินสด", "ชำระที่เคาน์เตอร์", KioskIcons.Payments, filled = true, onClick = {}, modifier = Modifier.weight(1f))
            ChoiceCard("QR พร้อมเพย์", "สแกนจ่ายผ่านแอปธนาคาร", KioskIcons.QrCode2, filled = false, onClick = {}, modifier = Modifier.weight(1f), loading = true)
        }
    }
}
