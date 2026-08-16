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
            .filter { ibanMod97(it.value.replace(" ", "")) }
            .map { Candidate(it.range) }

    /** Country code and last four; the BBAN body — letters included — goes. */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskAlnum(original, policy.maskChar, keepLeading = 2, keepTrailing = 4)
}
