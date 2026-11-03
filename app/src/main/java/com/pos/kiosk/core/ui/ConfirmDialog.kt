package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pos.kiosk.R
import com.pos.kiosk.core.session.reportUserInteraction
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.KioskTheme

/**
 * Kiosk-sized confirmation: an optional icon, the question, what will happen, then "Cancel" and the action
 * side by side at full button height. [destructive] paints the icon and the action in the error colour.
 * Tapping outside or pressing back is the same as "Cancel".
 */
@Composable
fun KioskConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    dismissText: String = stringResource(R.string.action_cancel),
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConfirmCard(title, message, confirmText, onConfirm, onDismiss, icon, destructive, dismissText, modifier)
    }
}

@Composable
private fun ConfirmCard(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector?,
    destructive: Boolean,
    dismissText: String,
    modifier: Modifier = Modifier,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        // A dialog is its own window: report touches so the idle timeout doesn't fire while it is open.
        modifier = modifier.reportUserInteraction().widthIn(max = 560.dp).fillMaxWidth().padding(24.dp),
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            icon?.let {
                Box(Modifier.size(72.dp).background(accent.tint(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(36.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = KioskInk, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = KioskInkMuted, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KioskOutlinedButton(dismissText, onDismiss, Modifier.weight(1f))
                KioskButton(confirmText, onConfirm, Modifier.weight(1f), containerColor = accent)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF808080)
@Composable
private fun KioskConfirmDialogPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) {
        ConfirmCard(
            title = "ลบรายการนี้?",
            message = "ลาเต้ × 2 จะถูกนำออกจากออเดอร์",
            confirmText = "ลบ",
            onConfirm = {},
            onDismiss = {},
            icon = KioskIcons.Delete,
            destructive = true,
            dismissText = "ยกเลิก",
        )
    }
}
