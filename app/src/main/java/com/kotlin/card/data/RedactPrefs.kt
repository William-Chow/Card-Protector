package com.kotlin.card.data

import android.content.Context
import android.content.SharedPreferences
import com.kotlin.card.filter.MAX_REVEALED_DIGITS
import com.kotlin.card.filter.MaskMode
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.keepCounts
import com.kotlin.card.ui.theme.ThemeMode

/** The mask glyphs the picker offers, in the order it shows them. */
val MASK_SYMBOLS = listOf('*', '•', '#', 'x', '$', '!', '@', '%', '^', '&')

/**
 * Four presentation toggles — theme, mask glyph, reveal mode, reveal count —
 * so the redaction sheet honours the choices made on the main screen, and so
 * the theme stops resetting on every cold start.
 *
 * **No input or output text is ever written here, and none ever should be.**
 * The app declares `android:allowBackup="true"` against an entirely commented-out
 * `xml/backup_rules.xml`, which means everything in this file is copied to the
 * user's cloud backup. A glyph preference is not sensitive. A card number is,
 * and putting one here would quietly break the "On-device" badge the app shows
 * in its own header.
 *
 * Plain [SharedPreferences] on purpose: `androidx.preference` and DataStore
 * would each be a new dependency for four integers.
 */
class RedactPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME_MODE, 0)) { ThemeMode.System }
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value.ordinal).apply()

    var maskSymbolIndex: Int
        get() = prefs.getInt(KEY_MASK_SYMBOL, 0).coerceIn(MASK_SYMBOLS.indices)
        set(value) = prefs.edit().putInt(KEY_MASK_SYMBOL, value).apply()

    var maskMode: MaskMode
        get() = MaskMode.entries.getOrElse(prefs.getInt(KEY_MASK_MODE, 0)) { MaskMode.LAST }
        set(value) = prefs.edit().putInt(KEY_MASK_MODE, value.ordinal).apply()

    var keepN: Int
        get() = prefs.getInt(KEY_KEEP_N, 4).coerceIn(0, MAX_REVEALED_DIGITS)
        set(value) = prefs.edit().putInt(KEY_KEEP_N, value).apply()

    /** The stored choices as the redactor sees them. */
    fun maskPolicy(): MaskPolicy {
        val (leading, trailing) = keepCounts(maskMode, keepN)
        return MaskPolicy(MASK_SYMBOLS[maskSymbolIndex], leading, trailing)
    }

    private companion object {
        const val NAME = "cardpro"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_MASK_SYMBOL = "maskSymbolIndex"
        const val KEY_MASK_MODE = "maskMode"
        const val KEY_KEEP_N = "keepN"
    }
}
