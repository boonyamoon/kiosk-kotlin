package com.pos.kiosk.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pos.kiosk.R
import com.pos.kiosk.core.session.LockMode
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.KioskTheme

/**
 * A small pill at the top of every customer screen while the app is neither pinned nor locked, so staff notice.
 * Tapping it asks Android to pin the app again (harmless if a customer taps it: they can only confirm the pin).
 */
@Composable
fun LockWarning(lockTask: LockTaskController, modifier: Modifier = Modifier) {
    val state by lockTask.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    AnimatedVisibility(state.mode == LockMode.NotPinned, modifier, enter = fadeIn(), exit = fadeOut()) {
        LockWarningPill(onClick = { activity?.let(lockTask::requestLock) })
    }
}

@Composable
private fun LockWarningPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
            .clip(CircleShape)
            .background(Color(0xE6111827))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(KioskIcons.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.lock_warning),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Preview
@Composable
private fun LockWarningPillPreview() {
    KioskTheme(brandColor = Color(0xFFFF5E00)) { LockWarningPill(onClick = {}) }
}
