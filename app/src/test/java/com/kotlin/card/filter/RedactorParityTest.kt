package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The load-bearing test. False positives are the primary failure mode of a
 * redactor: a detector that eats prices, dates, order numbers, postcodes or
 * version strings is worse than no detector, because the user stops trusting
 * the output and stops using the app.
 *
 * The assertion is deliberately *not* `redact(t) == t`. The shipped card pattern
 * already matches an ISBN, a 12-digit SKU and a 12-digit substring of a git SHA,
 * and has done since release — asserting the corpus is untouched would fail on
 * day one and prove nothing. The assertion is that the multi-type engine
 * reproduces [maskAllInText] byte for byte on this corpus: **the new detectors
 * add zero matches on non-sensitive text.**
 */
class RedactorParityTest {

    @Test
    fun `new detectors add nothing to ordinary text, at every slider position`() {
        for (line in FpCorpus.lines) {
            for (glyph in MASK_GLYPHS) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    val expected = maskAllInText(line, glyph, leading, trailing)
                    val actual = Redactor.redactAllInText(line, glyph, leading, trailing).output
                    assertEquals(
                        "glyph=$glyph keep=$leading/$trailing on: $line",
                        expected,
                        actual
                    )
                }
            }
        }
    }

    @Test
    fun `the corpus as one document behaves the same as line by line`() {
        val expected = maskAllInText(FpCorpus.joined, '*', 0, 4)
        assertEquals(expected, Redactor.redactAllInText(FpCorpus.joined, '*', 0, 4).output)
    }

    @Test
    fun `nothing in the corpus is reported as a detection`() {
        for (line in FpCorpus.lines) {
            val counts = Redactor.redactAllInText(line, '*', 0, 4).counts
            val newTypes = counts.keys - SensitiveType.CARD
            assertTrue("$line was claimed by $newTypes", newTypes.isEmpty())
        }
    }

    @Test
    fun `the corpus is big enough to be worth something`() {
        assertTrue("corpus shrank to ${FpCorpus.lines.size} lines", FpCorpus.lines.size >= 30)
    }
}
