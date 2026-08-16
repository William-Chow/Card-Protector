package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.ipv4Enumerated
import com.kotlin.card.filter.ipv4Public

/**
 * A dotted quad of 0–255 octets.
 *
 * The alphanumeric-and-dot lookbehind is what keeps `v1.10.4.2` and
 * `Chrome/120.0.6099.234` out; the trailing `(?![\d.])` stops a match landing in
 * the middle of a longer dotted string. Neither is sufficient on its own —
 * [ipv4Public] rejects two more whole classes of false positive, and
 * [ipv4Enumerated] a third.
 */
private val IPV4_REGEX = Regex(
    """(?<![\dA-Za-z.])(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)""" +
        """(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}(?![\d.])"""
)

/**
 * Public IPv4 addresses.
 *
 * Runs last. Private and reserved ranges are skipped entirely — masking
 * `192.168.1.10` or `127.0.0.1` makes a support log unreadable while hiding
 * nothing about anybody.
 *
 * Known residual, accepted and narrowed: a four-part version or section number
 * that names itself — `v1.10.4.2`, `Build 1.10.4.2`, `Section 12.4.5.6` — is
 * left alone, but an unlabelled one standing on its own in prose ("upgraded to
 * 1.10.4.2 yesterday") is still masked. Nothing structural separates that from a
 * real address in 1.0.0.0/8, and every guard that would catch it also drops real
 * addresses; masking it costs readability, not privacy.
 */
object Ipv4Detector : Detector {

    override val type = SensitiveType.IPV4

    override fun find(normalized: String): Sequence<Candidate> =
        IPV4_REGEX.findAll(normalized)
            .filter { ipv4Public(it.value) && !ipv4Enumerated(normalized, it.range.first) }
            .map { Candidate(it.range) }

    /**
     * Keeps the first octet — enough to place the network without identifying
     * the host — and collapses the rest to a fixed three glyphs each, so the
     * output does not leak how many digits each octet had.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String {
        val hidden = policy.maskChar.toString().repeat(3)
        val firstOctet = original.substringBefore('.')
        return "$firstOctet.$hidden.$hidden.$hidden"
    }
}
