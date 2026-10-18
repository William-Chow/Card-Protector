package com.kotlin.card.filter

/** Which digits stay visible. FIRST6_LAST4 reveals the BIN + last 4. */
enum class MaskMode { LAST, FIRST, FIRST6_LAST4 }

/** A card-number-like run: 12–19 digits, optionally separated by spaces or dashes. */
private val CARD_REGEX = Regex("""\d(?:[ \-]?\d){11,18}""")

/**
 * The most digits any mask may ever reveal, and therefore the slider's ceiling.
 * Ten is the industry limit — PCI DSS req. 3.3 allows at most the first six plus
 * the last four of a PAN to be displayed — so a 16-digit card always keeps at
 * least six digits hidden, whatever the slider is dragged to. It also leaves the
 * FIRST6_LAST4 preset (exactly ten) untouched for any card of 11+ digits.
 *
 * Every other sensitive type reuses this same clamp through [clampKeepCounts]
 * rather than declaring its own floor, so the shipped guarantee is inherited
 * instead of re-implemented. None of them asks for more than four, so the
 * ceiling itself never binds outside the card path — but the "at least one
 * character stays hidden" half of the clamp binds for all of them.
 */
const val MAX_REVEALED_DIGITS = 10

/** Resolve a [MaskMode] (+ a slider value N) into (keepLeading, keepTrailing). */
fun keepCounts(mode: MaskMode, keepN: Int): Pair<Int, Int> = when (mode) {
    MaskMode.LAST -> 0 to keepN
    MaskMode.FIRST -> keepN to 0
    MaskMode.FIRST6_LAST4 -> 6 to 4
}

/**
 * Cap requested keep counts against the number actually in hand, so a masked
 * result is never the raw number back again: at most [MAX_REVEALED_DIGITS] are
 * revealed, and at least one digit stays hidden however short the input is.
 * Leading (BIN-side) digits are surrendered first, so the conventional last-4
 * tail survives the clamp.
 */
fun clampKeepCounts(digitCount: Int, keepLeading: Int, keepTrailing: Int): Pair<Int, Int> {
    val maxRevealed = minOf(MAX_REVEALED_DIGITS, digitCount - 1).coerceAtLeast(0)
    val trailing = keepTrailing.coerceIn(0, maxRevealed)
    val leading = keepLeading.coerceIn(0, maxRevealed - trailing)
    return leading to trailing
}

/**
 * Mask a single number, revealing the first [keepLeading] and last [keepTrailing]
 * digits. Non-digit characters (spaces, dashes) are preserved as-is. Keep counts
 * are clamped by [clampKeepCounts] first, so over-large ones quietly reveal less
 * rather than the whole number — no error string.
 *
 * Walks code points, not `Char`s. A digit outside the BMP — the mathematical
 * bold `𝟒` — is two `Char`s, neither of them a digit on its own, so a `Char`
 * walk counted nothing, masked nothing and handed the number back whole. A
 * masked one becomes a single glyph, so the output can be shorter than the
 * input; a revealed one is kept whole, never split down the middle.
 */
fun maskNumber(account: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String =
    maskCodePoints(account, maskChar, keepLeading, keepTrailing, Character::isDigit)

/**
 * As [maskNumber], but letters are maskable too.
 *
 * An IBAN carries its account information in letters as well as digits, so
 * masking only the digits would leave most of the BBAN in plain sight. Keep
 * counts still route through [clampKeepCounts], so the same floor applies:
 * at least one alphanumeric always stays hidden.
 */
fun maskAlnum(value: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String =
    maskCodePoints(value, maskChar, keepLeading, keepTrailing, Character::isLetterOrDigit)

/** Mask the code points of [text] that are [maskable], keeping the ends asked for. */
private inline fun maskCodePoints(
    text: String,
    maskChar: Char,
    keepLeading: Int,
    keepTrailing: Int,
    maskable: (Int) -> Boolean
): String {
    var count = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (maskable(codePoint)) count++
        index += Character.charCount(codePoint)
    }
    if (count == 0) return text
    val (leading, trailing) = clampKeepCounts(count, keepLeading, keepTrailing)
    val sb = StringBuilder(text.length)
    var seen = 0
    index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val width = Character.charCount(codePoint)
        if (maskable(codePoint)) {
            val revealed = seen < leading || seen >= count - trailing
            if (revealed) sb.append(text, index, index + width) else sb.append(maskChar)
            seen++
        } else {
            sb.append(text, index, index + width)
        }
        index += width
    }
    return sb.toString()
}

/**
 * Mask every card-number-like run found in [text], leaving the rest untouched.
 *
 * **Kept deliberately, even though the UI now calls
 * [com.kotlin.card.filter.Redactor.redactAllInText] instead.** This is the
 * card-only regression oracle: `RedactorParityTest` asserts that the multi-type
 * engine reproduces this function byte-for-byte on text that contains nothing
 * sensitive, which is how new detectors are stopped from eating prices, dates,
 * order numbers and version strings. Deleting it deletes that gate.
 */
fun maskAllInText(text: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String =
    CARD_REGEX.replace(text) { match ->
        maskNumber(match.value, maskChar, keepLeading, keepTrailing)
    }

/** How many card-number-like runs are in [text]. */
fun countCards(text: String): Int = CARD_REGEX.findAll(text).count()

/**
 * Pull the digits of the first card-like run (for prefilling from a share), as
 * ASCII.
 *
 * Scanned folded, the way the redactor scans, because the redactor is what
 * decides a share holds exactly one card: a full-width number split by
 * ideographic spaces counts as one card there and matched nothing here, so the
 * prefill fell back to the raw full-width digits and the brand and Luhn checks,
 * which read ASCII, got them wrong.
 */
fun extractFirstCardDigits(text: String): String {
    val scan = ScanText.of(text).scan
    val match = CARD_REGEX.find(scan)
    val source = match?.value ?: scan
    return source.filter { it in '0'..'9' }.take(19)
}

/**
 * Every decimal digit in [text], in whatever script it was written, as ASCII,
 * and nothing else. What the single-card field keeps of a keystroke or a paste.
 */
fun asciiDigits(text: String): String = ScanText.of(text).scan.filter { it in '0'..'9' }
