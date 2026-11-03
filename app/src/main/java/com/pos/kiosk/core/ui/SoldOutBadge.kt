package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.theme.KioskTheme

/** Red "sold out" tag on a cart line whose product can't be ordered any more; the line must go before paying. */
@Composable
fun SoldOutBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.cart_item_sold_out),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SoldOutBadgePreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) { SoldOutBadge(Modifier.padding(16.dp)) }
}
