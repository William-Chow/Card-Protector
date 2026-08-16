package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.maskNumber

/**
 * A card-number-like run: 12–19 digits, optionally separated by spaces or
 * dashes. Copied verbatim from the shipped `CARD_REGEX` in `Masking.kt`, which
 * stays private there as the regression oracle. **This pattern must never drift
 * from that one** — the parity test is what notices if it does.
 *
 * Two things it deliberately does not have:
 *
 * - **No Luhn gate.** A redactor has to fail closed, and the shipped tests mask
 *   `123456787890`, which is not Luhn-valid. Gating on the checksum would leave
 *   mistyped and partial numbers in the clear.
 * - **No lookarounds.** Adding `(?<!\d)` / `(?!\d)` looks like a tightening but
 *   is a regression: on a 22-digit run this pattern masks the first 19 today,
 *   and an anchored version would match nothing at all. [CardDetector.expand]
 *   handles the overspill instead, and can only ever mask more.
 *
 * Visible to the rest of the module because [com.kotlin.card.filter.Redactor]
 * enforces it as a *floor* on every other detector's output too: a span that
 * some other type keeps verbatim may still not hand back a card run the shipped
 * masker would have hidden. One copy of the pattern, one place to drift from.
 */
internal val CARD_REGEX = Regex("""\d(?:[ \-]?\d){11,18}""")

/**
 * The catch-all numeric run, and the only type the Reveal slider governs.
 *
 * Runs second-to-last on purpose: everything with more structure has already had
 * its say, so an IC, an IBAN or a phone number is claimed by the detector that
 * understands it rather than being masked under card rules.
 */
object CardDetector : Detector {

    override val type = SensitiveType.CARD

    override fun find(normalized: String): Sequence<Candidate> =
        CARD_REGEX.findAll(normalized).map { Candidate(it.range) }

    /**
     * Swallow the rest of the run that the 19-digit ceiling left out.
     *
     * This closes two real leaks. The first: `1234567890123456789012` is masked
     * for its first nineteen digits and the last three stay in the clear.
     *
     * The second is why expansion now crosses a single space or dash as well as a
     * digit, and the earlier comment here — "never a space or a dash, which would
     * let one run merge into an unrelated neighbouring number" — had it exactly
     * backwards. The pattern already spans those separators; refusing to expand
     * across them does not keep two numbers apart, it just stops the claim
     * *part-way through* a run the pattern was already in the middle of. On
     * `123456789012<no-break space>4111 1111 1111 1111` the match ran out of
     * ceiling inside the PAN's third group and the last eight digits were left
     * for a detector that never came, because eight is below the twelve-digit
     * floor. Expanding to the end of the run masks the whole thing as one number,
     * which is also how a reader sees it.
     *
     * The cost is real and worth naming: two cards written one space apart are
     * masked as a single number, so the pair reveals one set of trailing digits
     * rather than two. That is *more* masking, in a redactor, on input that is
     * genuinely ambiguous — nothing in the text says whether it is one long
     * number or two short ones.
     *
     * A separator is crossed only when there is an unclaimed digit on the far
     * side of it, so expansion never trails off onto a lone space, never crosses
     * two separators in a row, and still stops dead at any span another detector
     * has already claimed.
     */
    override fun expand(
        normalized: String,
        range: IntRange,
        claimed: (Int) -> Boolean
    ): IntRange {
        var start = range.first
        var end = range.last
        while (true) {
            val previous = start - 1
            if (previous >= 0 && normalized[previous] in '0'..'9' && !claimed(previous)) {
                start = previous
                continue
            }
            if (previous >= 1 && normalized[previous].isRunSeparator() &&
                normalized[previous - 1] in '0'..'9' && !claimed(previous) && !claimed(previous - 1)
            ) {
                start = previous - 1
                continue
            }
            break
        }
        while (true) {
            val next = end + 1
            if (next < normalized.length && normalized[next] in '0'..'9' && !claimed(next)) {
                end = next
                continue
            }
            if (next + 1 < normalized.length && normalized[next].isRunSeparator() &&
                normalized[next + 1] in '0'..'9' && !claimed(next) && !claimed(next + 1)
            ) {
                end = next + 1
                continue
            }
            break
        }
        return start..end
    }

    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskNumber(original, policy.maskChar, policy.cardKeepLeading, policy.cardKeepTrailing)
}

/** The two characters [CARD_REGEX] tolerates between digits of one number. */
private fun Char.isRunSeparator(): Boolean = this == ' ' || this == '-'
