package com.kotlin.card.filter

/**
 * Fold the confusable characters a detector might trip over onto their plain
 * ASCII equivalents, **one character in, one character out**.
 *
 * The 1:1 rule is what keeps the common case free. Detectors scan the folded
 * string, but every replacement is computed from — and spliced back into — the
 * *original*, so an index has to mean the same thing in both strings. That rules
 * out NFKC, which changes length and would destroy the mapping. A test fuzzes
 * the mapped code points and asserts the length never moves. The one fold that
 * cannot be 1:1, a two-`Char` digit outside the BMP, is [ScanText]'s job.
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
    // Every dash — hyphen through horizontal bar, full-width and small
    // hyphen-minus, the CJK wave dash — and the minus sign, which is a math
    // symbol rather than a dash but looks like one.
    ch == '\u2212' || ch.category == CharCategory.DASH_PUNCTUATION -> '-'
    // Every space: no-break, narrow no-break, the U+2000 block, ideographic.
    ch.category == CharCategory.SPACE_SEPARATOR -> ' '
    // Every invisible format character: zero-width space, joiner and
    // non-joiner, word joiner, byte-order mark, and the bidi marks.
    ch.category == CharCategory.FORMAT -> ' '
    else -> ch
}

/**
 * The fold of a code point outside the BMP, or `null` when it does not fold.
 *
 * The same three families [fold] handles, and for the same reason: the
 * mathematical digits (`𝟒`, `𝟜`, `𝟺` and their siblings) are what a "fancy
 * text" generator turns `4111…` into, and they came back whole, with nothing in
 * the counts; the tag characters are invisible format characters, so one wedged
 * between two groups hid a card the same way a zero-width space used to.
 */
private fun foldSupplementary(codePoint: Int): Char? = when {
    Character.isDigit(codePoint) -> '0' + Character.digit(codePoint, 10)
    else -> when (Character.getType(codePoint)) {
        Character.DASH_PUNCTUATION.toInt() -> '-'
        Character.SPACE_SEPARATOR.toInt(), Character.FORMAT.toInt() -> ' '
        else -> null
    }
}

/**
 * The string the detectors read, and the way back from it to the text it came
 * from.
 *
 * [scan] is [normalizeForScan]'s fold with one more step: every code point
 * outside the BMP that folds — a digit, a dash, a space or an invisible format
 * character — is collapsed from its two `Char`s to the one ASCII character it
 * folds to. That is the only way a digit outside the BMP can be *one* digit to
 * the card pattern: folded in place, its two halves would be two characters, and
 * no rule about separators can make `𝟒𝟏𝟏𝟏 𝟏𝟏𝟏𝟏` read as one run that way.
 *
 * It is also the end of the 1:1 rule, so every index the detectors hand back is
 * a *scan* index, and [start] and [end] are how the redactor turns one into a
 * position in the original. When nothing collapses — which is every text that
 * does not contain such a code point — there is no map, [scan] is
 * [normalizeForScan]'s output, often the very same instance, and both lookups
 * are the identity.
 */
class ScanText private constructor(
    val scan: String,
    /** Where each scan position starts in the original, plus one entry for `scan.length`. */
    private val starts: IntArray?
) {

    /** Where scan position [index] begins in the original; [index] may be `scan.length`. */
    fun start(index: Int): Int = starts?.get(index) ?: index

    /** One past the original end of the scan span that ends at [last], inclusive. */
    fun end(last: Int): Int = start(last + 1)

    companion object {

        fun of(text: String): ScanText {
            val folded = normalizeForScan(text)
            if (!hasSupplementaryFold(text)) return ScanText(folded, null)
            val scan = StringBuilder(text.length)
            val starts = IntArray(text.length + 1)
            var at = 0
            while (at < text.length) {
                starts[scan.length] = at
                val pair = supplementaryAt(text, at)
                val collapsed = if (pair >= 0) foldSupplementary(pair) else null
                if (collapsed != null) {
                    scan.append(collapsed)
                    at += 2
                } else {
                    scan.append(folded[at])
                    at++
                }
            }
            starts[scan.length] = text.length
            return ScanText(scan.toString(), starts)
        }

        private fun hasSupplementaryFold(text: String): Boolean {
            for (index in 0 until text.length - 1) {
                val pair = supplementaryAt(text, index)
                if (pair >= 0 && foldSupplementary(pair) != null) return true
            }
            return false
        }

        /** The code point of the surrogate pair starting at [index], or -1 if there is none. */
        private fun supplementaryAt(text: String, index: Int): Int =
            if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                Character.toCodePoint(text[index], text[index + 1])
            } else {
                -1
            }
    }
}
