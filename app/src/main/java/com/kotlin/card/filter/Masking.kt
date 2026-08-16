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
 */
fun maskNumber(account: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String {
    val digitCount = account.count { it.isDigit() }
    if (digitCount == 0) return account
    val (leading, trailing) = clampKeepCounts(digitCount, keepLeading, keepTrailing)
    val sb = StringBuilder(account.length)
    var digitIndex = 0
    for (ch in account) {
        if (ch.isDigit()) {
            val revealed = digitIndex < leading || digitIndex >= digitCount - trailing
            sb.append(if (revealed) ch else maskChar)
            digitIndex++
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

/** Mask every card-number-like run found in [text], leaving the rest untouched. */
fun maskAllInText(text: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String =
    CARD_REGEX.replace(text) { match ->
        maskNumber(match.value, maskChar, keepLeading, keepTrailing)
    }

/** How many card-number-like runs are in [text]. */
fun countCards(text: String): Int = CARD_REGEX.findAll(text).count()

/** Pull the digits of the first card-like run (for prefilling from a share). */
fun extractFirstCardDigits(text: String): String {
    val match = CARD_REGEX.find(text)
    val source = match?.value ?: text
    return source.filter { it.isDigit() }.take(19)
}
