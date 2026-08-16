package com.kotlin.card.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kotlin.card.data.RedactPrefs
import com.kotlin.card.filter.summarizeCounts
import com.kotlin.card.ui.theme.CardProTheme
import com.kotlin.card.ui.theme.ThemeMode
import com.kotlin.card.ui.theme.successAccent

/**
 * The text-selection sheet — select text in any app, tap "Redact", get it back
 * masked in place.
 *
 * This is the differentiator, and until now it was only half wired up: the
 * PROCESS_TEXT filter existed, but nothing in the app ever called `setResult`,
 * so the entry point dropped the user into the full launcher screen — banner ad,
 * interstitial, in-app-update check and all — inside the host app's task, with a
 * manual copy-and-paste round trip to finish the job.
 *
 * Three things are deliberately absent here:
 *
 * - **No ads.** Firing an interstitial on the tap that returns the user to
 *   WhatsApp is the textbook accidental-click pattern.
 * - **No in-app update check.** It can raise a Snackbar inside another app's UI.
 * - **No saved state.** The output is a pure function of the intent and the
 *   stored preferences, so recreation is a non-issue and there is nothing to
 *   restore — which also means no sensitive text ever reaches a Bundle here.
 *
 * `launchMode` is left at the default `standard` on purpose: `singleTask` and
 * `singleInstance` make `startActivityForResult` return `RESULT_CANCELED`
 * immediately, which would silently break replacement forever.
 */
class RedactActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selection = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val prefs = RedactPrefs(this)
        val decision = decideRedaction(selection, readOnly, prefs.maskPolicy())

        setContent {
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            CardProTheme(darkTheme = darkTheme) {
                RedactSheet(
                    decision = decision,
                    onReplace = ::replaceSelection,
                    onCopy = { copyToClipboard(it) }
                )
            }
        }
    }

    /**
     * Hand the masked text back to the host app. Only ever on an explicit tap,
     * never automatically — and with no undo, by design: stashing the original
     * anywhere to support one would defeat the point of redacting it.
     */
    private fun replaceSelection(redacted: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, redacted))
        finish()
    }

    private fun copyToClipboard(redacted: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // A label, so the Android 13+ clipboard preview says what was copied.
        clipboard.setPrimaryClip(ClipData.newPlainText("Redacted text", redacted))
        finish()
    }
}

@Composable
private fun RedactSheet(
    decision: RedactDecision,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val result = decision.result
    val summary = summarizeCounts(result.counts)
    val found = decision.found
    val oversized = decision.oversized
    val readOnly = decision.readOnly
    val tooLongToReplace = decision.tooLongToReplace

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        oversized -> "Card Pro · selection is too large"
                        found -> "Card Pro · $summary found"
                        else -> "Card Pro · nothing sensitive found"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (found) colors.successAccent else colors.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = if (oversized) {
                                "This selection is over 200,000 characters. " +
                                    "Select a smaller piece of text and try again."
                            } else {
                                result.output
                            },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Replace is hidden outright when the host cannot take one,
                    // which promotes Copy to the full width; enablement then
                    // comes from the same predicate the unit tests pin.
                    if (!readOnly && !tooLongToReplace && !oversized) {
                        Button(
                            modifier = Modifier.weight(2f),
                            enabled = decision.canReplace,
                            onClick = { onReplace(result.output) }
                        ) { Text("Replace selection", maxLines = 1, softWrap = false) }
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = decision.canCopy,
                        onClick = { onCopy(result.output) }
                    ) { Text("Copy", maxLines = 1, softWrap = false) }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = when {
                        oversized -> "Nothing was changed."
                        readOnly -> "This app's text can't be edited — copy the masked version instead."
                        tooLongToReplace -> "Too long to replace in place — copy the masked version instead."
                        found -> "Replace changes the text in the other app. This can't be undone."
                        else -> "Nothing to mask, so there is nothing to replace."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
