package com.kotlin.card.filter

/**
 * Fold the confusable characters a detector might trip over onto their plain
 * ASCII equivalents, **one character in, one character out**.
 *
 * The 1:1 rule is the whole point. Detectors scan the normalized string, but
 * every replacement is computed from — and spliced back into — the *original*,
 * so an index has to mean the same thing in both strings. That rules out NFKC,
 * which changes length and would destroy the mapping. A test fuzzes the mapped
 * code points and asserts the length never moves.
 *
 * None of this is cosmetic: full-width and Arabic-Indic digits are how a card
 * number slips past an ASCII-only `\d`, and a zero-width space wedged into a
 * digit run does the same. Folding the zero-width characters to a plain space
 * lets the card pattern — which already tolerates spaces as separators — see
 * through that. Masking still reads the original substring, and `Char.isDigit()`
 * is Unicode-aware, so a full-width digit is masked as the digit it is.
 */
fun normalizeForScan(text: String): String {
    var changed = false
    val out = CharArray(text.length)
    for (index in text.indices) {
        val original = text[index]
        val folded = fold(original)
        if (folded != original) changed = true
        out[index] = folded
    }
    return if (changed) String(out) else text
}

private fun fold(ch: Char): Char = when (ch) {
    // Full-width digits, then Arabic-Indic digits
    in '\uFF10'..'\uFF19' -> '0' + (ch - '\uFF10')
    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
    // Hyphen through horizontal bar, and the minus sign
    in '\u2010'..'\u2015', '\u2212' -> '-'
    // No-break space and narrow no-break space
    '\u00A0', '\u202F' -> ' '
    // En/em/thin/figure and the rest of the U+2000 block spaces
    in '\u2000'..'\u200A' -> ' '
    // Zero-width space, non-joiner, joiner, and the byte-order mark
    '\u200B', '\u200C', '\u200D', '\uFEFF' -> ' '
    else -> ch
}
