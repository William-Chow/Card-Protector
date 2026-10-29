package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.ScanText
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.clampKeepCounts
import com.kotlin.card.filter.readsAsDigit

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
     * the address unchanged. A *digit* initial is never revealed at all: it says
     * nothing about who the address belongs to, and a local part can begin in
     * the middle of a number the shipped card masker hides —
     * `4111111111111111@x.com`, or a PAN that runs into an address — where
     * revealing it would mask one digit less than the engine it replaces. (The
     * domain side of the same rule is enforced for every type at once by the
     * card floor in `Redactor`.)
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String {
        // Found on the folded copy: the pattern matched there, and in the raw
        // text the `@` may be a full-width `＠` that a plain search misses —
        // which used to hand the whole address back unmasked.
        val scanned = ScanText.of(original)
        val atInScan = scanned.scan.lastIndexOf('@')
        if (atInScan <= 0) return original
        val at = scanned.start(atInScan)
        val local = original.substring(0, at)
        // Counted in code points: a local part can open with a two-`Char` digit
        // like `𝟒`, and revealing one `Char` of it would be half a character.
        val points = local.codePointCount(0, local.length)
        val (clamped, _) = clampKeepCounts(points, keepLeading = 1, keepTrailing = 0)
        val leading = if (readsAsDigit(local.codePointAt(0))) 0 else clamped
        return local.substring(0, local.offsetByCodePoints(0, leading)) +
            policy.maskChar.toString().repeat(points - leading) +
            original.substring(at)
    }
}
