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
 * Five phases, and the separation between them is deliberate:
 *
 * 1. **Normalize** ([normalizeForScan]) — strictly 1:1, so every index into the
 *    scanned string is also an index into the original.
 * 2. **Detect** — every detector runs over the whole normalized string. None of
 *    them ever sees partially masked output, which matters because `#`, `$` and
 *    `!` are all selectable mask glyphs and re-scanning masked text would let
 *    them form new matches.
 * 3. **Resolve** — first claim wins; an overlapping match is never partially
 *    applied, but neither is it simply thrown away: the unclaimed remainder is
 *    re-offered to the same detector (see [SCRUBBED]).
 * 4. **Sweep** ([sweepCardRuns]) — anything still unclaimed *inside* a
 *    card-length run is masked under card rules, whether or not a detector was
 *    willing to offer a candidate for it.
 * 5. **Replace, then enforce the ceiling** ([enforceRunCeiling]) — the rebuild
 *    walks spans by start position, so a replacement may be longer or shorter
 *    than what it replaces without invalidating any later offset; the assembled
 *    output is then checked against the one property that matters and masked
 *    further where it fails.
 *
 * **Why the last phase exists, and why it is not another detector fix.** Phases
 * 2–4 are boundary rules, and a boundary rule can be wrong in a way that leaks:
 * this branch shipped two rounds of "the boundary is right now", and an
 * adversarial reviewer found a larger family each time — first when a candidate
 * was *rejected* for overlap, then when a candidate was *accepted* without
 * covering its whole run. Phase 5 does not care where the boundaries fell. It
 * reads the finished text and enforces
 *
 * > no digit run of [CARD_RUN_MIN_DIGITS] or more digits in the input keeps more
 * > than [MAX_REVEALED_DIGITS] of its digits anywhere in the output
 *
 * directly, so a third boundary bug costs readability and not privacy.
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

    /**
     * A resolved span.
     *
     * [cards] is how many *card numbers* this claim accounts for, and it is not
     * always "one if this is a CARD claim". Two PANs one space apart are a single
     * card run and become a single claim, so a CARD claim reports what the
     * shipped masker would have counted in the same span rather than a flat 1 —
     * `4111111111111111 4111111111111111` is two cards, and saying "1 card found"
     * about it is the UI telling the user something false. A *non*-CARD claim
     * carries a non-zero [cards] when [cardFloor] had to hide a card run inside a
     * part of the span its own type keeps verbatim, which is how a link or an
     * email that swallowed a PAN still gets counted as having hidden one.
     */
    private class Claim(
        val range: IntRange,
        val type: SensitiveType,
        val replacement: String,
        val cards: Int
    )

    /** A replacement, and how many card runs [cardFloor] had to hide inside it. */
    private class Floored(val text: String, val cards: Int)

    /**
     * One piece of the assembled output, and where it came from.
     *
     * Verbatim segments are 1:1 with the input, so index arithmetic across them
     * is exact. A claim segment maps its whole input span onto its whole
     * replacement, because a replacement is free to change length.
     */
    private class Segment(
        val inFirst: Int,
        val inLast: Int,
        val outFirst: Int,
        val outLast: Int,
        val verbatim: Boolean
    )

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
                    val raw = detector.redact(original, candidate.copy(range = range), policy) ?: continue
                    val floored = cardFloor(raw, policy.maskChar)
                    val cards = if (detector.type == SensitiveType.CARD) {
                        cardNumbersIn(normalized.substring(range.first, range.last + 1))
                    } else {
                        floored.cards
                    }
                    for (index in range) claimed[index] = true
                    claimedCells += range.last - range.first + 1
                    claims += Claim(range, detector.type, floored.text, cards)
                }
                // Re-scan only while there is something left over *and* the picture
                // has actually changed since the copy just scanned. Every further
                // round needs a new claim, so this terminates.
                if (!residual || scannedAtCells == claimedCells) break
                scan = scrubClaimed(normalized, claimed)
                scannedAtCells = claimedCells
            }
        }
        val runs = cardLengthRuns(normalized)
        sweepCardRuns(text, normalized, runs, claimed, claims, policy.maskChar)
        if (claims.isEmpty()) return RedactResult(text, emptyMap())

        claims.sortBy { it.range.first }
        val segments = ArrayList<Segment>(claims.size * 2 + 1)
        val out = StringBuilder(text.length)
        var cursor = 0

        /** Record where the next `length` output characters came from. */
        fun mark(inFirst: Int, inLast: Int, length: Int, verbatim: Boolean) {
            segments += Segment(inFirst, inLast, out.length, out.length + length - 1, verbatim)
        }

        for (claim in claims) {
            if (cursor < claim.range.first) {
                mark(cursor, claim.range.first - 1, claim.range.first - cursor, verbatim = true)
                out.append(text, cursor, claim.range.first)
            }
            mark(claim.range.first, claim.range.last, claim.replacement.length, verbatim = false)
            out.append(claim.replacement)
            cursor = claim.range.last + 1
        }
        if (cursor < text.length) {
            mark(cursor, text.length - 1, text.length - cursor, verbatim = true)
            out.append(text, cursor, text.length)
        }

        val output = enforceRunCeiling(out.toString(), normalized, runs, segments, policy.maskChar)

        val counts = LinkedHashMap<SensitiveType, Int>()
        for (type in types) {
            val found = if (type == SensitiveType.CARD) {
                claims.sumOf { it.cards }
            } else {
                claims.count { it.type == type }
            }
            if (found > 0) counts[type] = found
        }
        return RedactResult(output, counts)
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
    private fun cardFloor(replacement: String, maskChar: Char): Floored {
        if (replacement.count { it in '0'..'9' } < CARD_RUN_MIN_DIGITS) return Floored(replacement, 0)
        var hidden = 0
        val masked = CARD_REGEX.replace(replacement) {
            hidden++
            maskNumber(it.value, maskChar, 0, 0)
        }
        return Floored(masked, hidden)
    }

    /** How many card numbers the shipped card-only masker would find in [span]. */
    private fun cardNumbersIn(span: String): Int =
        CARD_REGEX.findAll(span).count().coerceAtLeast(1)

    /**
     * Mask, under card rules, every digit still unclaimed inside a card-length
     * run.
     *
     * The case this exists for: `901231-14-5678 41111111111`. The IC is claimed,
     * and the eleven digits beside it are one short of the card pattern's floor,
     * so **no detector ever offers a candidate for them** — the resolver's
     * re-offer loop cannot help, because there is nothing to re-offer. Yet the
     * two together are a twenty-three digit run that the shipped masker hides
     * most of. The run is the unit a reader sees, so the run is the unit that
     * gets masked: anything left over inside one is a fragment of a number
     * somebody wrote down.
     *
     * **Reveals nothing, at every slider position**, and that is the point rather
     * than an oversight. The Reveal slider says "keep the last four digits *of a
     * card*"; a fragment left over after some other detector took the value out
     * of the middle of a run is not a card and has no meaningful last four. Its
     * own last four digits are simply four digits from the middle of somebody's
     * number, and handing those back is what every leak in this family did. So
     * the fragment goes entirely — `PO-2026-000123456789` masks its `2026` rather
     * than revealing `026`, which is also what the shipped masker does with it.
     *
     * Trimmed to digit boundaries so a claim never starts or ends on a separator,
     * and counted as a card because that is what it is a piece of — the counts
     * must never say "1 IC" about output where a numeric run was also hidden.
     */
    private fun sweepCardRuns(
        text: String,
        normalized: String,
        runs: List<DigitRun>,
        claimed: BooleanArray,
        claims: MutableList<Claim>,
        // Not a MaskPolicy: taking only the glyph is what makes "the Reveal
        // slider cannot reach a leftover fragment" a property of the signature
        // rather than a promise in the paragraph above.
        maskChar: Char
    ) {
        for (run in runs) {
            var index = run.range.first
            while (index <= run.range.last) {
                if (claimed[index]) {
                    index++
                    continue
                }
                var end = index
                while (end + 1 <= run.range.last && !claimed[end + 1]) end++
                var first = index
                var last = end
                while (first <= last && normalized[first] !in '0'..'9') first++
                while (last >= first && normalized[last] !in '0'..'9') last--
                if (first <= last) {
                    val original = text.substring(first, last + 1)
                    val replacement = maskNumber(original, maskChar, keepLeading = 0, keepTrailing = 0)
                    for (i in first..last) claimed[i] = true
                    claims += Claim(first..last, SensitiveType.CARD, replacement, cards = 1)
                }
                index = end + 1
            }
        }
    }

    /**
     * The backstop: **no card-length run keeps more than [MAX_REVEALED_DIGITS] of
     * its digits**, however the detectors carved it up.
     *
     * Applied to the assembled output rather than to each replacement in
     * isolation, because in isolation every replacement here is already within
     * its own budget and the leak is in the *sum*: a card detector revealing ten
     * digits of one part of a run, a phone detector revealing three of another,
     * and a fragment nobody claimed revealing all of itself, add up to a number a
     * reader can use while no single rule was broken.
     *
     * Digits are surrendered from the **left**, matching [clampKeepCounts]'s rule
     * that BIN-side digits go first, so what survives is still the conventional
     * tail. Digits inside a claim that merely *straddles* the run — an IBAN's
     * last four, an address's first octet — are counted against the ceiling but
     * masked only as a last resort: they are the ones most likely to belong to
     * the neighbouring value rather than to this run.
     *
     * Nothing here is length-changing, so the output stays aligned with itself,
     * and no mask glyph the picker offers is a digit, so masking can only ever
     * reduce the count it is measuring.
     */
    private fun enforceRunCeiling(
        assembled: String,
        normalized: String,
        runs: List<DigitRun>,
        segments: List<Segment>,
        maskChar: Char
    ): String {
        if (runs.isEmpty()) return assembled
        val chars = assembled.toCharArray()
        // Where each replacement's surviving digits sit in the output, computed
        // once. A single claim can contain several runs — a PEM block, a long
        // URL — and re-deriving this per run would make the pass quadratic in
        // exactly the 64 KB inputs PROCESS_TEXT can hand it.
        val claimDigits = arrayOfNulls<List<Int>>(segments.size)
        for (index in segments.indices) {
            val segment = segments[index]
            if (segment.verbatim) continue
            claimDigits[index] = (segment.outFirst..segment.outLast).filter { chars[it].isDigit() }
        }
        // Runs and segments are both sorted by input position and runs do not
        // overlap, so one forward pointer covers every run without rescanning.
        var from = 0
        for (run in runs) {
            while (from < segments.size && segments[from].inLast < run.range.first) from++
            val inRun = ArrayList<Int>()
            val straddling = ArrayList<Int>()
            var straddlingCount = 0
            var cursor = from
            while (cursor < segments.size && segments[cursor].inFirst <= run.range.last) {
                val segment = segments[cursor]
                val at = cursor
                cursor++
                if (segment.inLast < run.range.first) continue
                if (segment.verbatim) {
                    val first = maxOf(segment.inFirst, run.range.first)
                    val last = minOf(segment.inLast, run.range.last)
                    for (index in first..last) {
                        val out = segment.outFirst + (index - segment.inFirst)
                        if (chars[out].isDigit()) inRun += out
                    }
                    continue
                }
                // Re-checked rather than trusted: a claim that straddles two runs
                // is visited twice, and the first visit may already have masked
                // some of these positions.
                val digits = claimDigits[at].orEmpty().filter { chars[it].isDigit() }
                if (digits.isEmpty()) continue
                if (segment.inFirst >= run.range.first && segment.inLast <= run.range.last) {
                    inRun += digits
                    continue
                }
                // Straddles the boundary: it cannot reveal more of *this* run than
                // this run has digits inside it, whatever else the claim covered.
                val overlap = (maxOf(segment.inFirst, run.range.first)..minOf(segment.inLast, run.range.last))
                    .count { normalized[it] in '0'..'9' }
                straddlingCount += minOf(digits.size, overlap)
                straddling += digits
            }
            var excess = inRun.size + straddlingCount - MAX_REVEALED_DIGITS
            if (excess <= 0) continue
            for (index in inRun) {
                if (excess == 0) break
                chars[index] = maskChar
                excess--
            }
            for (index in straddling) {
                if (excess == 0) break
                chars[index] = maskChar
                excess--
            }
        }
        return String(chars)
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
