package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.clampKeepCounts

/**
 * A local part, an `@`, and a domain with a real-looking final label.
 *
 * The `{1,64}` on the local part is what declines bare `@handle` mentions, and
 * the lookarounds stop a match starting or ending mid-token. Runs after URL, so
 * `https://user@host/x` and `mailto:` forms are already claimed as links.
 */
private val EMAIL_REGEX = Regex(
    """(?<![A-Za-z0-9._%+\-])[A-Za-z0-9._%+\-]{1,64}""" +
        """@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,24}(?![A-Za-z0-9\-])"""
)

/** Email addresses. */
object EmailDetector : Detector {

    override val type = SensitiveType.EMAIL

    override fun find(normalized: String): Sequence<Candidate> =
        EMAIL_REGEX.findAll(normalized).map { Candidate(it.range) }

    /**
     * First character of the local part, then the domain in full:
     * `siti.aminah@sekolah.edu.my` becomes `s**********@sekolah.edu.my`.
     *
     * Keeping the whole domain is an explicit product call. For the dominant
     * consumer case — gmail.com, yahoo.com — masking it hides nothing and costs
     * the reader the only context they had. The counter-case is real: a
     * one-person company domain *is* the person, and this is the first rule to
     * revisit if that bites.
     *
     * The single revealed character routes through [clampKeepCounts], so a
     * one-character local part reveals nothing at all rather than handing back
     * the address unchanged.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String {
        val at = original.lastIndexOf('@')
        if (at <= 0) return original
        val local = original.substring(0, at)
        val (leading, _) = clampKeepCounts(local.length, keepLeading = 1, keepTrailing = 0)
        return local.take(leading) +
            policy.maskChar.toString().repeat(local.length - leading) +
            original.substring(at)
    }
}
