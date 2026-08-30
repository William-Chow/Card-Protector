package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.LineIndex
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import com.kotlin.card.filter.cardLengthRuns
import com.kotlin.card.filter.cardRunLines
import com.kotlin.card.filter.expiryLabelled
import com.kotlin.card.filter.maskNumber

/**
 * `MM/YY` or `MM/YYYY`, over a `/` or a `-`.
 *
 * Three parts of this are load-bearing, and each one kills a whole family of
 * false positive that the corpus in `fp_corpus.txt` is full of:
 *
 * - **The month is two digits, `01`–`12`.** `1/2 cup`, `3/4`, `SS15/4B` and
 *   every other fraction and ratio are gone in one rule, and a single-digit
 *   month is not a shape any card is printed in.
 * - **`.` is not a separator.** `Section 12.4.5.6`, `Version 2.10.4.2` and
 *   `Chrome/120.0.6099.234` all contain something a dotted rule would claim.
 * - **The boundary classes exclude digits, letters, `/` and `-`.** This is what
 *   makes a full date structurally unable to produce a match: in `16/08/2026`
 *   the `08/2026` is preceded by `/` and the `16/08` is followed by one, so
 *   neither seam is offered. `2026-08-16` fails the same way on the dash. That
 *   matters more than it looks — `DD/MM/YYYY` is the standard form in the market
 *   this app is aimed at, and the corpus carries it twice.
 *
 * The trailing `\.\d` exclusion refuses `12/26.5` while still allowing a real
 * expiry to end a sentence.
 */
private val EXPIRY_REGEX = Regex(
    """(?<![0-9A-Za-z/.\-])(?:0[1-9]|1[0-2])[/\-](?:\d{2}|20\d{2})(?![0-9A-Za-z/\-]|\.\d)"""
)

/**
 * Card expiry dates.
 *
 * **Anchored, never bare**, and that is the whole design. `12/26` is the weakest
 * shape in this app: no checksum, no length, no structure, and it is also a
 * fraction, a score line and a date range. So the pattern above only produces a
 * *candidate*, and a candidate is claimed only when the surrounding text says
 * what it is — either an expiry label sits in front of it ([expiryLabelled]) or
 * a card-length digit run shares its line ([cardRunLines]).
 *
 * Running it unanchored was tried and is exactly what `RedactorParityTest`
 * exists to stop: the gate there is that a new detector adds *zero* matches on
 * ordinary text, and an unanchored `MM/YY` fails it on the first date in the
 * corpus.
 *
 * Outranks CARD so that `12/26 4111111111111111` — one long run to the ruler —
 * is split at the seam a reader sees rather than reported as one card. It cannot
 * ever *contest* a span with CARD: CARD floors at twelve digits and this never
 * reaches five.
 *
 * **Accepted residual, named rather than hidden:** an `MM/YY`-shaped token that
 * is not an expiry — "passed 12/26 of the checks" — is claimed when a
 * card-length run happens to share its line. Nothing structural separates the
 * two, the guards that would catch it also drop real expiries, and the cost is
 * four characters of readability on a line that was already being masked. The
 * same token with no card on the line, or written with a dash, is left alone;
 * `fp_corpus.txt` gates both.
 */
object CardExpiryDetector : Detector {

    override val type = SensitiveType.CARD_EXPIRY

    override fun find(normalized: String): Sequence<Candidate> = sequence {
        val matches = EXPIRY_REGEX.findAll(normalized).toList()
        // Nothing shaped like an expiry means neither anchor is worth computing,
        // which is what keeps this off the hot path for the 64 KB inputs
        // PROCESS_TEXT can hand over.
        if (matches.isEmpty()) return@sequence
        val lines = LineIndex(normalized)
        val runLines = cardRunLines(lines, cardLengthRuns(normalized))
        for (match in matches) {
            // The two anchors do not accept the same shapes, and the asymmetry is
            // the point: how loose the shape may be depends on how good the
            // evidence is. A label is direct evidence, so it carries either
            // separator. A card on the same line is circumstantial, so it carries
            // only the `MM/YY` a card is actually printed in — `12-26` next to an
            // account number is far more often a range ("items 12-26") than an
            // expiry, and claiming it would cost readability on exactly the
            // invoices and order confirmations this app is pointed at.
            val anchored = expiryLabelled(normalized, match.range.first) ||
                (match.value.contains('/') && lines.lineOf(match.range.first) in runLines)
            if (anchored) yield(Candidate(match.range))
        }
    }

    /**
     * Reveals nothing, at every slider position.
     *
     * There is no useful half of an expiry date. The month alone is one of
     * twelve and the year alone is one of about ten, so revealing either is a
     * meaningful fraction of the guess — and an expiry is only ever worth
     * anything to somebody who already has the PAN, which is precisely the
     * situation the rest of this engine is trying to prevent. The separator
     * survives, as it does for an IC, so the output still reads as a date.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        maskNumber(original, policy.maskChar, keepLeading = 0, keepTrailing = 0)
}
