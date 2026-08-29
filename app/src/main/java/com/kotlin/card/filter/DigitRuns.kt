package com.kotlin.card.filter

/**
 * A **card-length run**: the unit the privacy ceiling is defined over.
 *
 * [range] indexes the normalized string — and therefore the original, since
 * [normalizeForScan] is 1:1 — and [digits] is how many of those characters are
 * digits.
 */
data class DigitRun(val range: IntRange, val digits: Int)

/**
 * The fewest digits that make a run worth a ceiling: the same twelve the card
 * pattern floors at, so a run this engine treats as card-length is exactly a run
 * the shipped masker would have tried to hide.
 */
const val CARD_RUN_MIN_DIGITS = 12

/**
 * Every maximal run in [normalized] holding at least [CARD_RUN_MIN_DIGITS]
 * digits, in order, non-overlapping.
 *
 * Runs are found on the **normalized** string on purpose. That is where a
 * no-break space, a zero-width joiner and a figure dash have already become
 * `' '` and `'-'`, so `123456789012 4111 1111 1111 1111` is the single
 * twenty-eight digit run it looks like to a reader — and not the two separate
 * numbers the raw-text card pattern sees, which is the gap two rounds of
 * adversarial review found leaks in.
 *
 * A run is digits joined by at most one space or dash — the same separators the
 * shipped `CARD_REGEX` spans, but unbounded where that pattern stops at nineteen
 * digits. The bounded version is a *matcher*: it answers "is there a card here".
 * This is a *ruler*: it answers "how far does the number the user pasted actually
 * reach", which is the question the nineteen-digit ceiling cannot answer and the
 * question every leak found on this branch turned on.
 *
 * **Written as a loop rather than as `Regex("""\d(?:[ \-]?\d)*""")`, and that is
 * not a micro-optimisation.** The regex form is a nested quantifier over an
 * unbounded run and java.util.regex matches it by recursion: on the 66 000-digit
 * input `PerfTest` fires at it, it does not merely run slowly, it throws
 * `StackOverflowError` — inside a PROCESS_TEXT handler, which is the *host* app's
 * UI thread. This loop is one left-to-right pass, no backtracking, no stack.
 */
fun cardLengthRuns(normalized: String): List<DigitRun> {
    val runs = ArrayList<DigitRun>()
    val length = normalized.length
    var index = 0
    while (index < length) {
        if (normalized[index] !in '0'..'9') {
            index++
            continue
        }
        val first = index
        var last = index
        var digits = 0
        while (index < length) {
            val ch = normalized[index]
            if (ch in '0'..'9') {
                digits++
                last = index
                index++
                continue
            }
            // A separator continues the run only when a digit follows it, so a run
            // never trails off onto a space and never crosses two in a row.
            if ((ch == ' ' || ch == '-') && index + 1 < length && normalized[index + 1] in '0'..'9') {
                index++
                continue
            }
            break
        }
        if (digits >= CARD_RUN_MIN_DIGITS) runs += DigitRun(first..last, digits)
    }
    return runs
}
