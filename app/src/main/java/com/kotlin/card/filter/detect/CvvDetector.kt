package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.maskNumber

/**
 * A CVV label, then the three or four digits behind it.
 *
 * The label is not optional and never will be. Three digits is the single most
 * common shape in any text — a quantity, a price, a room number, a page, an age
 * — so an unlabelled rule would not be a detector, it would be a redaction of
 * every small number on the page. Anchoring on the label is the entire evidence,
 * exactly as it is for the `password: …` rule in [SecretDetector].
 *
 * `cid` is deliberately **absent** from the vocabulary despite being a real
 * card-verification abbreviation on Amex: it is also "correlation id" and
 * "customer id" in every support log this app is pointed at, and a label that
 * ambiguous makes the output worse without hiding anything a card holder cares
 * about. `csc` and `cv2` are unambiguous enough to keep.
 *
 * The whitespace either side of the separator is bounded rather than `\s*`: a
 * label and its value that are four whitespace characters apart are still the
 * same field, and anything further apart is two different facts that happen to
 * be near each other.
 */
private val CVV_REGEX = Regex(
    """(?i)\b(?:cvv2?|cvc2?|cv2|csc|security\s+code|card\s+verification\s+(?:value|code)|""" +
        """kod\s+keselamatan)\b\s{0,4}[:=#-]?\s{0,4}(\d{3,4})(?!\d)"""
)

/**
 * Card verification codes.
 *
 * Only the digits are claimed — the label is left standing, which is the same
 * call [SecretDetector] makes for a `Bearer` scheme and the difference between a
 * redactor and a delete key. `CVV: 123` becomes `CVV: ***`, so the reader can
 * see that a code was there and that it is gone.
 *
 * The trailing `(?!\d)` means a longer run behind the label is not a CVV and is
 * left for CARD, which is the right answer for `cvv 4111111111111111` — somebody
 * pasted the wrong field, and it is a card number whatever the label says.
 */
object CvvDetector : Detector {

    override val type = SensitiveType.CVV

    override fun find(normalized: String): Sequence<Candidate> =
        CVV_REGEX.findAll(normalized).mapNotNull { match ->
            match.groups[1]?.range?.takeIf { !it.isEmpty() }?.let { Candidate(it) }
        }

    /**
     * Reveals nothing, at every slider position. A CVV is three digits; there is
     * no fraction of it that is safe to keep, and unlike a PAN it has no
     * conventional tail a reader needs in order to tell two of them apart.
     *
     * Masked one-for-one rather than collapsed to a fixed width the way a secret
     * is. The length of a CVV is not a fingerprint worth hiding — it is three
     * digits, or four on an Amex whose BIN is already visible in the card above
     * it — and keeping the length keeps the line aligned with what it replaced.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskNumber(original, policy.maskChar, keepLeading = 0, keepTrailing = 0)
}
