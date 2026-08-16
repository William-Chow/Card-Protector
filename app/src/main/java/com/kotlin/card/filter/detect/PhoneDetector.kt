package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.maskNumber
import com.kotlin.card.filter.phoneDigitsPlausible

/**
 * Malaysian numbers in national (`03-1234 5678`, `012-345 6789`) or
 * international (`+60 12-345 6789`) form: mobile `01X`, Klang Valley `03`, and
 * the remaining `04`–`09` area codes.
 */
private val MY_PHONE_REGEX = Regex(
    """(?<![0-9A-Za-z+_])(?:\+60[ \-]?|0)""" +
        """(?:1[0-46-9][ \-]?\d{3,4}[ \-]?\d{4}|3[ \-]?\d{4}[ \-]?\d{4}|[4-9][ \-]?\d{3}[ \-]?\d{4})""" +
        """(?![0-9])"""
)

/**
 * Anything else in E.164 form. The `+` must be followed immediately by a digit,
 * which is both the false-positive defence (`+ 60 more seats` never matches) and
 * the tiebreaker that lets a phone number outrank the card run inside it: the
 * phone span starts one character earlier, at the `+`.
 */
private val E164_PHONE_REGEX = Regex("""(?<![0-9A-Za-z+_])\+[1-9]\d{0,2}[ \-]?\d(?:[ \-]?\d){6,13}(?![0-9])""")

/**
 * Phone numbers, Malaysian first.
 *
 * The governing rule, and the reason this does not eat every long number in
 * sight: **a digit run with no `+` and no leading `0` is never a phone.** That
 * one restriction is what leaves order numbers, invoice references, postcodes,
 * timestamps and thousands-separated amounts alone. The trailing `(?!\d)` is
 * what stops a sixteen-digit card that happens to begin with `0` from being
 * claimed here instead of by CARD.
 */
object PhoneDetector : Detector {

    override val type = SensitiveType.PHONE

    override fun find(normalized: String): Sequence<Candidate> = sequence {
        // Malaysian forms are emitted first so they win the overlap against the
        // generic international pattern, which would otherwise cut a +60 number
        // short at a different boundary.
        yieldAll(MY_PHONE_REGEX.findAll(normalized))
        yieldAll(E164_PHONE_REGEX.findAll(normalized))
    }
        .filter { phoneDigitsPlausible(nationalDigits(it.value)) }
        // `+60` is a country code, not identity, and keeping it readable is worth
        // three characters. Everything after it is masked but the last three.
        .map { Candidate(it.range, keepPrefix = if (it.value.startsWith("+60")) 3 else 0) }

    /**
     * Reveals the last three digits, not the last four.
     *
     * With a known Malaysian operator prefix, the last four digits are enough to
     * serve as a bank's identity-confirmation token, which is exactly the thing
     * a redacted screenshot should not carry.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String {
        val prefix = original.take(candidate.keepPrefix)
        val body = original.drop(candidate.keepPrefix)
        return prefix + maskNumber(body, policy.maskChar, keepLeading = 0, keepTrailing = 3)
    }
}

/** The number's digits with any `+60` or trunk `0` prefix stripped. */
private fun nationalDigits(match: String): String {
    val digits = match.filter { it in '0'..'9' }
    return when {
        match.startsWith("+60") -> digits.removePrefix("60")
        digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }
}
