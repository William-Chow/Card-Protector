package com.kotlin.card.filter

/** Which digits stay visible. FIRST6_LAST4 reveals the BIN + last 4. */
enum class MaskMode { LAST, FIRST, FIRST6_LAST4 }

/** A card-number-like run: 12–19 digits, optionally separated by spaces or dashes. */
private val CARD_REGEX = Regex("""\d(?:[ \-]?\d){11,18}""")

/** Resolve a [MaskMode] (+ a slider value N) into (keepLeading, keepTrailing). */
fun keepCounts(mode: MaskMode, keepN: Int): Pair<Int, Int> = when (mode) {
    MaskMode.LAST -> 0 to keepN
    MaskMode.FIRST -> keepN to 0
    MaskMode.FIRST6_LAST4 -> 6 to 4
}

/**
 * Mask a single number, revealing the first [keepLeading] and last [keepTrailing]
 * digits. Non-digit characters (spaces, dashes) are preserved as-is. Over-large
 * keep counts simply reveal everything — no error string.
 */
fun maskNumber(account: String, maskChar: Char, keepLeading: Int, keepTrailing: Int): String {
    val digitCount = account.count { it.isDigit() }
    if (digitCount == 0) return account
    val sb = StringBuilder(account.length)
    var digitIndex = 0
    for (ch in account) {
        if (ch.isDigit()) {
            val revealed = digitIndex < keepLeading || digitIndex >= digitCount - keepTrailing
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
