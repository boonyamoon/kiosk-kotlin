package com.pos.kiosk.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pos.kiosk.R
import com.pos.kiosk.core.ui.icons.KioskIcons
import com.pos.kiosk.core.ui.theme.MeedColors

/** Rounded white field with a leading icon — the MeeD login style. */
@Composable
fun MeedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    label: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Follows the store's theme_color once paired; before pairing the theme is the MeeD orange.
    val primary = MaterialTheme.colorScheme.primary
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder, color = MeedColors.Placeholder, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, modifier = Modifier.padding(start = 8.dp)) },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primary,
            unfocusedBorderColor = MeedColors.Border,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = primary,
            focusedLeadingIconColor = primary,
            unfocusedLeadingIconColor = MeedColors.Icon,
            focusedLabelColor = primary,
        ),
    )
}

/** [MeedTextField] with a lock icon and a show/hide toggle. */
@Composable
fun MeedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    MeedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = KioskIcons.Lock,
        modifier = modifier,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = keyboardActions,
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) KioskIcons.Visibility else KioskIcons.VisibilityOff,
                    contentDescription = stringResource(if (visible) R.string.setup_hide_password else R.string.setup_show_password),
                    tint = MeedColors.Icon,
                )
            }
        },
    )
}

/** Orange gradient pill with a trailing icon (an arrow by default). */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector = KioskIcons.ArrowForward,
) {
    val shape = RoundedCornerShape(20.dp)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .alpha(if (enabled || loading) 1f else 0.6f)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = primary, spotColor = primary)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(primary, lerp(primary, Color.White, 0.27f))))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Icon(icon, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FormErrorLine(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(KioskIcons.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    }
}
