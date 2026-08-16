package com.kotlin.card.filter

import com.kotlin.card.filter.detect.CardDetector
import com.kotlin.card.filter.detect.EmailDetector
import com.kotlin.card.filter.detect.IbanDetector
import com.kotlin.card.filter.detect.Ipv4Detector
import com.kotlin.card.filter.detect.MyNricDetector
import com.kotlin.card.filter.detect.PhoneDetector
import com.kotlin.card.filter.detect.SecretDetector
import com.kotlin.card.filter.detect.UrlDetector

/**
 * A span a detector would like to claim, plus the one thing a detector needs to
 * remember about it between finding and masking.
 *
 * [keepPrefix] is how many leading characters of the span survive verbatim: the
 * brand marker of an API key (`sk-`, `AKIA`), or the `+60` of a Malaysian number
 * written in international form. It is a count of characters in the *original*
 * substring, not of revealed digits.
 */
data class Candidate(val range: IntRange, val keepPrefix: Int = 0)

/**
 * The user's one knob, and the exact scope of it.
 *
 * [maskChar] applies to every type. [cardKeepLeading] / [cardKeepTrailing] come
 * from the Reveal slider and are read by the CARD detector **only** — every
 * other type carries a fixed safe default baked into its own `redact`. That is
 * what makes "the slider is card-only" a structural property of this type rather
 * than a promise in a label, and it is asserted mechanically by SliderScopeTest.
 */
data class MaskPolicy(
    val maskChar: Char,
    val cardKeepLeading: Int = 0,
    val cardKeepTrailing: Int = 4
)

/** Finds and masks one family of sensitive values. */
interface Detector {

    val type: SensitiveType

    /**
     * Every candidate span in [normalized], in the order they should be claimed.
     * Validation belongs here, not in [redact]: a candidate that fails its
     * checksum or structural test must never be yielded, or it would claim a
     * span that a lower-priority detector should have had.
     */
    fun find(normalized: String): Sequence<Candidate>

    /**
     * The replacement for [original] — the *raw* text of the span, not the
     * normalized form — or `null` to **release** the span so a lower-priority
     * detector can still claim it. Releasing is how a bare `https://example.com`
     * avoids being counted and mangled when it hides nothing.
     */
    fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String?

    /**
     * Widen a raw match before it is claimed, stopping short of any index for
     * which [claimed] is true. Only CARD uses this, to swallow the digits that
     * spill past its 19-digit ceiling. Default: no expansion.
     */
    fun expand(normalized: String, range: IntRange, claimed: (Int) -> Boolean): IntRange = range
}

/** The redacted text, plus how many of each type were masked to produce it. */
data class RedactResult(val output: String, val counts: Map<SensitiveType, Int>)

/**
 * Turns text into redacted text.
 *
 * Three phases, and the separation between them is deliberate:
 *
 * 1. **Normalize** ([normalizeForScan]) — strictly 1:1, so every index into the
 *    scanned string is also an index into the original.
 * 2. **Detect** — every detector runs over the whole normalized string. None of
 *    them ever sees partially masked output, which matters because `#`, `$` and
 *    `!` are all selectable mask glyphs and re-scanning masked text would let
 *    them form new matches.
 * 3. **Resolve and replace** — first claim wins; an overlapping match is dropped
 *    whole, never partially applied. The rebuild walks spans by start position,
 *    so a replacement may be longer or shorter than what it replaces without
 *    invalidating any later offset.
 */
object Redactor {

    /**
     * Detectors in priority order. The list is asserted to be sorted by
     * [SensitiveType.priority] on first use, because a mis-ordered registry is a
     * silent privacy regression rather than a crash: an IC would be masked under
     * card rules and its date of birth would survive.
     */
    private val DETECTORS: List<Detector> = listOf(
        SecretDetector,
        UrlDetector,
        EmailDetector,
        IbanDetector,
        MyNricDetector,
        PhoneDetector,
        CardDetector,
        Ipv4Detector
    ).also { detectors ->
        require(detectors.map { it.type.priority } == detectors.map { it.type.priority }.sorted()) {
            "Detector registry must be ordered by SensitiveType.priority"
        }
    }

    /** The types the registry can actually produce, in resolution order. */
    val types: List<SensitiveType> get() = DETECTORS.map { it.type }

    private class Claim(val range: IntRange, val type: SensitiveType, val replacement: String)

    /** Mask every sensitive value in [text], leaving the rest byte-for-byte alone. */
    fun redactAllInText(text: String, policy: MaskPolicy): RedactResult {
        if (text.isEmpty()) return RedactResult(text, emptyMap())
        val normalized = normalizeForScan(text)
        val claimed = BooleanArray(normalized.length)
        val claims = ArrayList<Claim>()

        for (detector in DETECTORS) {
            for (candidate in detector.find(normalized)) {
                if (candidate.range.isEmpty()) continue
                val range = detector.expand(normalized, candidate.range) { claimed[it] }
                if ((range.first..range.last).any { claimed[it] }) continue
                val original = text.substring(range.first, range.last + 1)
                val replacement =
                    detector.redact(original, candidate.copy(range = range), policy) ?: continue
                for (index in range) claimed[index] = true
                claims += Claim(range, detector.type, replacement)
            }
        }
        if (claims.isEmpty()) return RedactResult(text, emptyMap())

        claims.sortBy { it.range.first }
        val out = StringBuilder(text.length)
        var cursor = 0
        for (claim in claims) {
            out.append(text, cursor, claim.range.first)
            out.append(claim.replacement)
            cursor = claim.range.last + 1
        }
        out.append(text, cursor, text.length)

        val counts = LinkedHashMap<SensitiveType, Int>()
        for (type in types) {
            val found = claims.count { it.type == type }
            if (found > 0) counts[type] = found
        }
        return RedactResult(out.toString(), counts)
    }

    /**
     * Convenience overload matching the shape of the shipped [maskAllInText], so
     * the two can be compared directly by the parity test.
     */
    fun redactAllInText(
        text: String,
        maskChar: Char,
        keepLeading: Int,
        keepTrailing: Int
    ): RedactResult = redactAllInText(text, MaskPolicy(maskChar, keepLeading, keepTrailing))
}

/**
 * "2 cards · 1 phone · 1 IC", or an empty string for nothing found. Lives here
 * rather than in the UI so it stays testable without an Android runtime.
 */
fun summarizeCounts(counts: Map<SensitiveType, Int>): String =
    counts.entries
        .sortedBy { it.key.priority }
        .joinToString(" · ") { (type, count) ->
            "$count ${if (count == 1) type.label else type.plural}"
        }
