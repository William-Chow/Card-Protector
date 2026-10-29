package com.kotlin.card.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kotlin.card.R
import com.kotlin.card.ui.theme.CardGradBottom
import com.kotlin.card.ui.theme.CardGradMid
import com.kotlin.card.ui.theme.CardGradTop
import com.kotlin.card.ui.theme.Gold
import com.kotlin.card.ui.theme.OnHeroMuted
import com.kotlin.card.ui.theme.Success
import com.kotlin.card.ui.theme.TextPrimary

/**
 * The masked result rendered as the visual hero: a credit-card mockup whose
 * kept (revealed) trailing digits glow mint while masked glyphs stay muted.
 */
@Composable
internal fun CardHero(
    masked: String,
    brand: String,
    luhnOk: Boolean,
    hasInput: Boolean,
    pulse: Int
) {
    // Fixed, not colorScheme-derived: the hero is dark in both themes.
    val muted = OnHeroMuted
    // Resolved here rather than inside the semantics lambda below, which is
    // not a composable scope.
    val description = heroDescription(masked, brand, luhnOk, hasInput)
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            scale.snapTo(0.96f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(CardGradTop, CardGradMid, CardGradBottom))
            )
            .padding(20.dp)
            // The number below is built one character at a time so the kept
            // digits can be tinted, which leaves a screen reader reciting
            // "asterisk asterisk asterisk…" sixteen times and never saying what
            // it is looking at. One coherent sentence replaces the whole card.
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gold)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = brand,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (luhnOk) {
                        Text(
                            text = stringResource(R.string.hero_valid),
                            color = Success,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (hasInput) {
                Text(
                    text = buildHeroNumber(masked, muted),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = "•••• •••• •••• ••••",
                    color = muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (hasInput) R.string.hero_footer_masked else R.string.hero_footer_empty
                ),
                color = muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * What the hero says to a screen reader.
 *
 * Counts rather than recites: sixteen mask glyphs read out individually are
 * noise, and the useful facts are the brand, how much is hidden and which digits
 * survived. The revealed digits are spaced so they are read one by one — "1 1 1
 * 1" rather than "one thousand one hundred and eleven".
 *
 * Built from three nested templates rather than by gluing fragments together, so
 * each one stays a whole phrase a translator can reorder.
 */
@Composable
private fun heroDescription(
    masked: String,
    brand: String,
    luhnOk: Boolean,
    hasInput: Boolean
): String {
    if (!hasInput) return stringResource(R.string.hero_footer_empty)
    val revealed = masked.filter { it.isDigit() }
    val hidden = masked.count { !it.isDigit() && it != ' ' && it != '-' }
    var text = stringResource(
        R.string.hero_a11y,
        brand,
        pluralStringResource(R.plurals.digits_hidden, hidden, hidden)
    )
    if (revealed.isNotEmpty()) {
        text = stringResource(R.string.hero_a11y_showing, text, revealed.toCharArray().joinToString(" "))
    }
    if (luhnOk) text = stringResource(R.string.hero_a11y_valid, text)
    return text
}

/**
 * Two-tone the masked string in a single annotated string: kept digits in mint,
 * mask glyphs muted. Separators are dropped and the value is regrouped into 4s.
 */
private fun buildHeroNumber(masked: String, mutedColor: Color): AnnotatedString {
    val compact = masked.filter { it != '-' && it != ' ' }
    return buildAnnotatedString {
        compact.forEachIndexed { index, c ->
            if (index != 0 && index % 4 == 0) {
                withStyle(SpanStyle(color = mutedColor.copy(alpha = 0.4f))) { append(' ') }
            }
            if (c.isDigit()) {
                withStyle(SpanStyle(color = Success, fontWeight = FontWeight.SemiBold)) { append(c) }
            } else {
                withStyle(SpanStyle(color = mutedColor)) { append(c) }
            }
        }
    }
}

