package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.maskNumber
import com.kotlin.card.filter.nricValid

/**
 * Twelve digits, optionally broken by the conventional dashes: YYMMDD-PB-###G.
 *
 * The alphanumeric lookarounds are the load-bearing part. `(?<![\d\-])` — the
 * obvious choice — fires inside a git SHA such as `deadbeef901231145678cafe`,
 * because `f` is neither a digit nor a dash. Requiring a non-alphanumeric on
 * both sides is what keeps ICs out of SHAs, SKUs and build identifiers.
 */
private val NRIC_REGEX = Regex("""(?<![0-9A-Za-z_])\d{6}[- ]?\d{2}[- ]?\d{4}(?![0-9A-Za-z_])""")

/**
 * Malaysian MyKad numbers.
 *
 * Outranks CARD, and that reordering is the fix for a shipped leak: a twelve
 * digit IC separated by single dashes matches the card pattern today, so
 * `901231-14-5678` under the 6+4 preset renders as `901231-**-5678` — the full
 * date of birth in the clear, and the gender parity digit with it.
 */
object MyNricDetector : Detector {

    override val type = SensitiveType.MY_NRIC

    override fun find(normalized: String): Sequence<Candidate> =
        NRIC_REGEX.findAll(normalized)
            .filter { nricValid(it.value.filter { ch -> ch in '0'..'9' }) }
            .map { Candidate(it.range) }

    /**
     * Reveals nothing, at every slider position.
     *
     * The first six digits are a date of birth and the last four end in the
     * gender parity digit; either half plus a name re-identifies a person. This
     * is a deliberate change from the card-rule behaviour it replaces, and the
     * one place where the new engine is *less* readable than the old one on
     * purpose.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskNumber(original, policy.maskChar, keepLeading = 0, keepTrailing = 0)
}
