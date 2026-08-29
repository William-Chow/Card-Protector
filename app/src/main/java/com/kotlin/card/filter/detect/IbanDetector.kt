package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.ibanMod97
import com.kotlin.card.filter.maskAlnum

/**
 * Two letters, two check digits, then 11–30 more alphanumerics, each of which
 * may be preceded by a single space.
 *
 * The `[ ]?` is not decoration: without it the printed grouping every European
 * bank uses — `GB29 NWBK 6016 1331 9268 19` — is missed entirely, which is the
 * form an IBAN actually arrives in when someone pastes it out of an invoice.
 */
private val IBAN_REGEX = Regex("""(?<![A-Za-z0-9])[A-Z]{2}\d{2}(?:[ ]?[A-Z0-9]){11,30}(?![A-Za-z0-9])""")

/**
 * International bank account numbers.
 *
 * Outranks CARD because an IBAN contains a card-length digit run —
 * `MT84MALT011000012345MTLCAST001S` holds `011000012345`, a clean twelve-digit
 * hit for the card pattern — so without this the middle of an IBAN would be
 * masked and both ends left readable.
 *
 * The mod-97 check runs in [find] rather than [redact] on purpose: a reference
 * like `MY24INV000123456789` matches the shape but fails the checksum, and it
 * has to fail *before* the span is claimed so CARD can still mask the digit run
 * inside it exactly as it does today.
 */
object IbanDetector : Detector {

    override val type = SensitiveType.IBAN

    override fun find(normalized: String): Sequence<Candidate> =
        IBAN_REGEX.findAll(normalized)
            .mapNotNull { match -> longestValidIban(match)?.let { Candidate(match.range.first..it) } }

    /**
     * The end index of the longest prefix of [match] that passes mod-97, or
     * `null` if none does.
     *
     * `find` gets exactly one shot per starting position, and the pattern's
     * `(?:[ ]?[A-Z0-9]){11,30}` runs happily across the single spaces of a
     * *neighbouring* number: on `GB29NWBK60161331926819 4111 1111 1111 1111` the
     * greedy match ate three groups of the card, mod-97 failed on the result, and
     * the IBAN was reported as absent — no IBAN in the counts, and the country's
     * bank and branch codes left in the clear. The retry walks back to each space
     * inside the match, longest first, so the real IBAN is found and the card is
     * left for the card detector.
     *
     * Only space boundaries are tried, and that is deliberate. Truncating at an
     * arbitrary character would offer dozens of candidates to a checksum that
     * passes one in ninety-seven by chance, which is how a checksum stops being
     * evidence; the failure being fixed is specifically the match running *across
     * a space* into a neighbour, so those are the only places worth looking.
     */
    private fun longestValidIban(match: MatchResult): Int? {
        val value = match.value
        val start = match.range.first
        val ends = sequenceOf(value.length) +
            value.indices.reversed().asSequence().filter { value[it] == ' ' }
        for (length in ends) {
            if (length <= 0 || !value[length - 1].isLetterOrDigit()) continue
            val compact = value.take(length).replace(" ", "")
            if (compact.length !in 15..34) continue
            if (ibanMod97(compact)) return start + length - 1
        }
        return null
    }

    /** Country code and last four; the BBAN body — letters included — goes. */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskAlnum(original, policy.maskChar, keepLeading = 2, keepTrailing = 4)
}
