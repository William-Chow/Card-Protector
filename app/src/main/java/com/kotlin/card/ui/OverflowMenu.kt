package com.kotlin.card.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlin.card.R
import com.kotlin.card.ui.theme.ThemeMode

/**
 * Header overflow menu for switching between System / Light / Dark themes.
 *
 * The trigger is a bare `⋮` glyph rather than an icon, which made it two
 * separate accessibility failures: a screen reader announced the character and
 * nothing about what it does, and at `titleLarge` plus 10dp of padding the touch
 * target came out well under the 48dp minimum. The glyph is now decorative and
 * the box around it carries the role, the label and the size.
 */
@Composable
internal fun OverflowMenu(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    privacyOptionsRequired: Boolean,
    onPrivacyOptions: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val themeName = stringResource(
        when (themeMode) {
            ThemeMode.System -> R.string.theme_system
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.Dark -> R.string.theme_dark
        }
    )
    val menuLabel = stringResource(R.string.theme_menu, themeName)
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .semantics {
                    role = Role.Button
                    contentDescription = menuLabel
                }
                .clickable { expanded = true }
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options = listOf(
                stringResource(R.string.theme_system) to ThemeMode.System,
                stringResource(R.string.theme_light) to ThemeMode.Light,
                stringResource(R.string.theme_dark) to ThemeMode.Dark
            )
            options.forEach { (label, mode) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onThemeChange(mode)
                        expanded = false
                    },
                    trailingIcon = {
                        if (themeMode == mode) Text(text = "✓", color = colors.primary)
                    }
                )
            }
            // Shown only where the consent SDK says it is required, which is
            // where a user who consented must be able to change their mind. An
            // app that gathers consent correctly on first run and then offers no
            // way back is still out of policy.
            if (privacyOptionsRequired) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.privacy_options)) },
                    onClick = {
                        expanded = false
                        onPrivacyOptions()
                    }
                )
            }
        }
    }
}

