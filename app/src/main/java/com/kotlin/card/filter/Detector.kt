package com.kotlin.card.filter

import com.kotlin.card.filter.detect.CARD_REGEX
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
 * 3. **Resolve and replace** — first claim wins; an overlapping match is never
 *    partially applied, but neither is it simply thrown away: the unclaimed
 *    remainder is re-offered to the same detector (see [SCRUBBED]). The rebuild
 *    walks spans by start position, so a replacement may be longer or shorter
 *    than what it replaces without invalidating any later offset.
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

    /**
     * What a claimed span looks like to a detector that re-scans after it.
     *
     * A newline, because it is the one character every pattern here already
     * treats as a hard boundary: it is not a digit, not alphanumeric, not a `.`,
     * not one of the single space / dash separators the card pattern spans, and
     * it terminates the greedy `[^\s…]+` of the URL rule and the `\S+` of the
     * secret rules. Scrubbing to it is therefore the honest picture of what the
     * text will look like once the claim is masked: a boundary, not a bridge.
     *
     * Substituting in place keeps the string 1:1 with [normalizeForScan]'s
     * output, so a re-scan still yields indices that mean the same thing in the
     * original text, and a lookbehind still sees real context on the side that
     * was never claimed.
     */
    private const val SCRUBBED = '\n'

    /** Mask every sensitive value in [text], leaving the rest byte-for-byte alone. */
    fun redactAllInText(text: String, policy: MaskPolicy): RedactResult {
        if (text.isEmpty()) return RedactResult(text, emptyMap())
        val normalized = normalizeForScan(text)
        val claimed = BooleanArray(normalized.length)
        var claimedCells = 0
        val claims = ArrayList<Claim>()

        for (detector in DETECTORS) {
            // The first pass always reads the pristine string, so every lookaround
            // sees the context it was written against. Re-scans read a copy with
            // the claims so far scrubbed out — see [SCRUBBED].
            var scan = normalized
            var scannedAtCells = -1
            while (true) {
                var residual = false
                for (candidate in detector.find(scan)) {
                    if (candidate.range.isEmpty()) continue
                    val range = detector.expand(normalized, candidate.range) { claimed[it] }
                    val free = (range.first..range.last).count { !claimed[it] }
                    if (free < range.last - range.first + 1) {
                        // Overlaps a claim, so it cannot be masked as one value —
                        // but the part nobody has claimed is still in the clear,
                        // and dropping the candidate whole is what left a full PAN
                        // beside a masked IC. Note it, re-offer it below.
                        if (free > 0) residual = true
                        continue
                    }
                    val original = text.substring(range.first, range.last + 1)
                    val replacement = detector.redact(original, candidate.copy(range = range), policy)
                        ?.let { cardFloor(it, policy.maskChar) } ?: continue
                    for (index in range) claimed[index] = true
                    claimedCells += range.last - range.first + 1
                    claims += Claim(range, detector.type, replacement)
                }
                // Re-scan only while there is something left over *and* the picture
                // has actually changed since the copy just scanned. Every further
                // round needs a new claim, so this terminates.
                if (!residual || scannedAtCells == claimedCells) break
                scan = scrubClaimed(normalized, claimed)
                scannedAtCells = claimedCells
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

    /**
     * The floor under every replacement: a detector that keeps part of its span
     * verbatim may not keep a card-length digit run the shipped masker hides.
     *
     * Two real cases, both a full PAN in the clear on this branch before it was
     * added, and both reachable from the PROCESS_TEXT sheet: a URL keeps its
     * authority (`https://4111111111111111.example.com/x`) and an email keeps
     * its domain (`a@4111111111111111.com`). Rather than patching those two
     * detectors and waiting for the third, the rule lives here, where it covers
     * every type that exists now or later.
     *
     * Safe to run on a replacement even though replacements are partly masked
     * already: no glyph the picker offers is a digit, a space or a dash, so a
     * mask glyph can only ever *break* a run, never join one. Length is
     * preserved, and the run is masked outright rather than at the slider
     * position, which keeps this independent of the Reveal control — the run is
     * not the value this type is masking, so the card slider does not govern it.
     */
    private fun cardFloor(replacement: String, maskChar: Char): String {
        if (replacement.count { it in '0'..'9' } < 12) return replacement
        return CARD_REGEX.replace(replacement) { maskNumber(it.value, maskChar, 0, 0) }
    }

    /** [normalized] with every claimed index replaced by [SCRUBBED], length unchanged. */
    private fun scrubClaimed(normalized: String, claimed: BooleanArray): String {
        val out = CharArray(normalized.length)
        for (index in normalized.indices) {
            out[index] = if (claimed[index]) SCRUBBED else normalized[index]
        }
        return String(out)
    }
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
