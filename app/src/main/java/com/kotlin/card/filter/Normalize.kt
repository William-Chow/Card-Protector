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
 *
 * **Folded by Unicode category, not by a list of code points.** This used to be
 * a hand-picked table — full-width and Arabic-Indic digits, the U+2000 spaces,
 * four zero-width characters — and every character it left out was a working
 * bypass: `４１１１　１１１１　１１１１　１１１１` (full-width digits split by the
 * ideographic space a CJK keyboard types), Persian or Devanagari digits, and a
 * soft hyphen or word joiner wedged between groups each came back with all
 * sixteen digits in the clear and nothing in the counts. The masker already
 * counts every Unicode digit as a digit; the scanner has to see every one too,
 * and every space, dash and invisible format character that can sit between
 * them. `NormalizeTest` checks the whole Basic Multilingual Plane against
 * `FOLDED_SEPARATORS`, so a fold cannot widen without the adjacency suites
 * running over it.
 *
 * Accepted residual: digits outside the BMP — the mathematical bold `𝟒` family —
 * are two `Char`s each and cannot be folded one-for-one, so they are not seen.
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

private fun fold(ch: Char): Char = when {
    // Nothing in ASCII folds, and ASCII is nearly every character this sees.
    ch < '\u0080' -> ch
    // Every decimal digit system in the BMP: full-width, Arabic-Indic and its
    // Persian form, Devanagari, Bengali, Thai and the rest.
    ch.isDigit() -> '0' + ch.digitToInt()
    // A soft hyphen draws nothing unless a line breaks on it. A dash rather than
    // the space the other invisible characters get, because it sits *inside*
    // words: as a space it would cut a password or a URL path in two.
    ch == '\u00AD' -> '-'
    // Every dash \u2014 hyphen through horizontal bar, full-width and small
    // hyphen-minus, the CJK wave dash \u2014 and the minus sign, which is a math
    // symbol rather than a dash but looks like one.
    ch == '\u2212' || ch.category == CharCategory.DASH_PUNCTUATION -> '-'
    // Every space: no-break, narrow no-break, the U+2000 block, ideographic.
    ch.category == CharCategory.SPACE_SEPARATOR -> ' '
    // Every invisible format character: zero-width space, joiner and
    // non-joiner, word joiner, byte-order mark, and the bidi marks.
    ch.category == CharCategory.FORMAT -> ' '
    else -> ch
}
