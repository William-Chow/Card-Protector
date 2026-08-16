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
 */
private val CARD_REGEX = Regex("""\d(?:[ \-]?\d){11,18}""")

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
     * Swallow digits adjacent to the match that the 19-digit ceiling left out.
     *
     * This closes a real leak: `1234567890123456789012` is masked for its first
     * nineteen digits today and the last three stay in the clear. Expansion
     * crosses ASCII digits only — never a space or a dash, which would let one
     * run merge into an unrelated neighbouring number — and stops dead at any
     * span another detector has already claimed.
     */
    override fun expand(
        normalized: String,
        range: IntRange,
        claimed: (Int) -> Boolean
    ): IntRange {
        var start = range.first
        var end = range.last
        while (start > 0 && normalized[start - 1] in '0'..'9' && !claimed(start - 1)) start--
        while (end < normalized.length - 1 && normalized[end + 1] in '0'..'9' && !claimed(end + 1)) end++
        return start..end
    }

    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskNumber(original, policy.maskChar, policy.cardKeepLeading, policy.cardKeepTrailing)
}
