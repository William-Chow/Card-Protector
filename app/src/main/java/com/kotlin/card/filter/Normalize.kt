package com.kotlin.card.filter

import java.text.Normalizer

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
    ch.code < 0x80 -> ch
    // Every decimal digit system in the BMP: full-width, Arabic-Indic and its
    // Persian form, Devanagari, Bengali, Thai and the rest.
    ch.isDigit() -> '0' + ch.digitToInt()
    // A soft hyphen draws nothing unless a line breaks on it. A dash rather than
    // the space the other invisible characters get, because it sits *inside*
    // words: as a space it would cut a password or a URL path in two.
    ch.code == 0xAD -> '-'
    // Every dash — hyphen through horizontal bar, full-width and small
    // hyphen-minus, the CJK wave dash — and the minus sign, which is a math
    // symbol rather than a dash but looks like one.
    ch.code == 0x2212 || ch.category == CharCategory.DASH_PUNCTUATION -> '-'
    // Every space: no-break, narrow no-break, the U+2000 block, ideographic.
    ch.category == CharCategory.SPACE_SEPARATOR -> ' '
    // Every invisible format character: zero-width space, joiner and
    // non-joiner, word joiner, byte-order mark, and the bidi marks.
    ch.category == CharCategory.FORMAT -> ' '
    // Styled and full-width ASCII: `ｈｔｔｐｓ：／／`, `²`, `Ⓐ`, `ℝ`.
    else -> COMPAT[ch.code] ?: ch
}

/**
 * The fold of a code point outside the BMP, or `null` when it does not fold.
 *
 * The same families [fold] handles, and for the same reason: the mathematical
 * alphabets and digits (`𝐀`, `𝟒`, `𝟜`, `𝟺` and their siblings) are what a
 * "fancy text" generator turns plain text into, and a card, a key or an email
 * written in them came back whole; the tag characters are invisible format
 * characters, so one wedged between two groups hid a card the same way a
 * zero-width space used to.
 */
private fun foldSupplementary(codePoint: Int): Char? = when {
    Character.isDigit(codePoint) -> '0' + Character.digit(codePoint, 10)
    else -> when (Character.getType(codePoint)) {
        Character.DASH_PUNCTUATION.toInt() -> '-'
        Character.SPACE_SEPARATOR.toInt(), Character.FORMAT.toInt() -> ' '
        else -> COMPAT[codePoint]
    }
}

/**
 * Where the styled and full-width copies of ASCII live: the characters a
 * "fancy text" generator, a CJK keyboard or a copy from a formatted document
 * turns plain letters, digits and punctuation into.
 *
 * Kept to these blocks on purpose. The table below is built from Unicode's own
 * compatibility mapping (NFKC), and asking for that per character across the
 * whole of Unicode would put a normaliser call on every CJK character of a 64 KB
 * paste. These blocks hold every styled form of ASCII that NFKC knows about:
 * the bold, italic, script, fraktur, double-struck, sans-serif and monospace
 * alphabets, full-width ASCII, circled and squared letters, superscripts,
 * subscripts and the "tiny text" modifier letters.
 */
internal val COMPAT_BLOCKS: List<IntRange> = listOf(
    0x00A0..0x00BF,   // ª º ¹ ² ³
    0x1D2C..0x1DBF,   // modifier letters: ᴬ ᵇ ᶜ
    0x2070..0x209F,   // superscripts and subscripts: ⁴ ₂ ⁿ
    0x2100..0x214F,   // letterlike symbols: ℂ ℍ ℕ ℝ ℤ ℯ
    0x2460..0x24FF,   // circled digits and letters: ① Ⓐ ⓐ
    0xFF01..0xFF5E,   // full-width ASCII: Ａ ａ ＠ ． ／
    0x1D400..0x1D7FF, // mathematical alphanumerics: 𝐀 𝑨 𝒜 𝔄 𝔸 𝖠 𝙰
    0x1F100..0x1F1FF  // squared and circled letters: 🄰 🄫
)

/** Each code point in [COMPAT_BLOCKS] whose NFKC form is one printable ASCII character. */
private val COMPAT: Map<Int, Char> by lazy {
    val map = HashMap<Int, Char>()
    for (block in COMPAT_BLOCKS) {
        for (codePoint in block) {
            val form = Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFKC)
            if (form.length == 1 && form[0] in '!'..'~') map[codePoint] = form[0]
        }
    }
    map
}

/** What one code point reads as to the detectors: its fold, or `null` when it does not fold. */
internal fun foldCodePoint(codePoint: Int): Char? =
    if (codePoint <= 0xFFFF) {
        val ch = codePoint.toChar()
        fold(ch).takeIf { it != ch }
    } else {
        foldSupplementary(codePoint)
    }

/**
 * Whether [codePoint] reads as a digit to the detectors, and so has to be masked
 * as one.
 *
 * Wider than `Character.isDigit`, and that is the point: `²` and `④` are not
 * decimal digits to Unicode, but they fold to `2` and `4`, so a card written in
 * them is a card to the scanner. A masker that disagreed would claim the number
 * and hand every digit of it back.
 */
internal fun readsAsDigit(codePoint: Int): Boolean =
    codePoint in 0x30..0x39 || foldCodePoint(codePoint)?.let { it in '0'..'9' } == true

/** As [readsAsDigit], for letters too: `Ⓐ` and `🄰` are symbols to Unicode and letters to a reader. */
internal fun readsAsLetterOrDigit(codePoint: Int): Boolean =
    Character.isLetterOrDigit(codePoint) || foldCodePoint(codePoint)?.isLetterOrDigit() == true

/**
 * The string the detectors read, and the way back from it to the text it came
 * from.
 *
 * [scan] is [normalizeForScan]'s fold with one more step: every code point
 * outside the BMP that folds — a digit, a styled letter, a dash, a space or an
 * invisible format character — is collapsed from its two `Char`s to the one
 * ASCII character it folds to. That is the only way a digit outside the BMP can be *one* digit to
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
