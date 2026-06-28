package com.kotlin.card.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-teal ink that stays legible on the mint (Success) CTA / chips
val OnMint = Color(0xFF04342C)

// ── Vault palette ────────────────────────────────────────────────────────────
val Base = Color(0xFF0A0E1A)           // app background — near-black navy
val SurfaceElevated = Color(0xFF12182B) // control sheet
val SurfaceInput = Color(0xFF0E1322)    // text fields / banner slot
val ChipResting = Color(0xFF1E2236)     // unselected chips / disabled
val Primary = Color(0xFF7C5CFC)         // brand violet
val Success = Color(0xFF34E0A1)         // kept-digit mint / confirmation
val Gold = Color(0xFFE8C77E)            // EMV chip + contactless arcs
val GoldShade = Color(0xFF9C7E3A)       // chip contact lines
val TextPrimary = Color(0xFFF4F6FF)     // soft white
val TextMuted = Color(0xFF8893B2)       // labels / helper text
val OutlineColor = Color(0xFF2E3552)    // hairline borders
val ErrorColor = Color(0xFFFF6B7A)      // inline validation

val CardGradTop = Color(0xFF1E2A4A)
val CardGradMid = Color(0xFF3A2C6B)
val CardGradBottom = Color(0xFF0C1430)

private val VaultColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Success,
    onSecondary = OnMint,
    background = Base,
    onBackground = TextPrimary,
    surface = SurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = ChipResting,
    onSurfaceVariant = TextMuted,
    outline = OutlineColor,
    error = ErrorColor,
    onError = Color.White,
)

// Parallel light scheme so the app honors the system theme. Primary is darkened
// to #5B43E0 to pass WCAG AA on white; the hero card keeps its navy gradient in
// both modes (see CardGrad* tokens) for brand consistency.
private val DayColors = lightColorScheme(
    primary = Color(0xFF5B43E0),
    onPrimary = Color.White,
    secondary = Success,
    onSecondary = OnMint,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0A0E1A),
    surface = Color.White,
    onSurface = Color(0xFF0A0E1A),
    surfaceVariant = Color(0xFFE7EAF2),
    onSurfaceVariant = Color(0xFF5B6172),
    outline = Color(0xFFCBD2E0),
    error = ErrorColor,
    onError = Color.White,
)

/** User-selectable theme preference, surfaced in the header overflow menu. */
enum class ThemeMode { System, Light, Dark }

@Composable
fun CardProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VaultColors else DayColors,
        content = content
    )
}
