package com.kotlin.card.filter

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * **The gate.** Everything else in this package tests a rule; this tests the
 * property those rules exist to produce.
 *
 * > For any input, no digit run of twelve or more digits present in the input may
 * > have more than [MAX_REVEALED_DIGITS] of its digits surviving anywhere in the
 * > output.
 *
 * Why this and not the older comparison against the card-only masker. The
 * previous gate — `digitCount(output) <= digitCount(cardOnlyOracle)` — measures
 * the engine against a baseline that is itself allowed to leak: the shipped
 * `CARD_REGEX` stops at nineteen digits, so on a twenty-eight digit run it hides
 * nineteen and hands back the other nine, and an engine that matched it exactly
 * would pass while leaking nine digits. Worse, the oracle reads the *raw* text
 * and the engine reads [normalizeForScan]'s output, so the two do not even agree
 * on where a run begins: twenty-four Unicode characters fold onto `' '` or `'-'`
 * and join two numbers into one run for the engine and leave them separate for
 * the oracle. Two rounds of review found leaks in exactly that gap.
 *
 * This property is absolute. It names no oracle, it does not care which detector
 * claimed what, and it is the same statement at every slider position — the
 * ceiling is the ceiling. [Redactor] enforces it directly as a final pass over
 * the assembled output.
 *
 * **What the measurement is, and why it is sound rather than exact.** Counting
 * "digits of *this* run that survived" would need a per-character map from output
 * back to input, and building one here would just be a second copy of the
 * production backstop marking its own homework. Instead this counts globally:
 * every digit in the output is a copy of some digit in the input (no detector
 * invents digits — masking either preserves position or keeps a verbatim slice),
 * so
 *
 *     digits(output) <= digits(input outside card-length runs)
 *                       + MAX_REVEALED_DIGITS * (number of card-length runs)
 *
 * is implied by the property, and any violation of the bound is a real violation
 * of the property. It cannot raise a false alarm, and on the shapes that matter —
 * one card-length run in a little prose — it is exact.
 */
class CardRunCeilingTest {

    /** A maximal card-style run: digits joined by at most one space or dash. */
    private val digitRun = Regex("""\d(?:[ \-]?\d)*""")

    private class Census(val cardLengthRuns: Int, val digitsOutsideRuns: Int)

    /**
     * The runs of [input] as the engine sees them — that is, after folding, which
     * is the whole point: `123456789012 4111 1111 1111 1111` is *one*
     * twenty-eight digit run to the engine and two numbers to the shipped masker.
     */
    private fun census(input: String): Census {
        val normalized = normalizeForScan(input)
        var runs = 0
        var digitsInRuns = 0
        for (match in digitRun.findAll(normalized)) {
            val digits = match.value.count { it in '0'..'9' }
            if (digits >= 12) {
                runs++
                digitsInRuns += digits
            }
        }
        val total = normalized.count { it in '0'..'9' }
        return Census(runs, total - digitsInRuns)
    }

    /** The ceiling, checked. Returns silently when there is no card-length run. */
    private fun assertCeiling(input: String, policy: MaskPolicy, label: String = "") {
        val census = census(input)
        if (census.cardLengthRuns == 0) return
        val result = Redactor.redactAllInText(input, policy)
        val ceiling = census.digitsOutsideRuns + MAX_REVEALED_DIGITS * census.cardLengthRuns
        val survived = digitCount(result.output)
        assertTrue(
            "${if (label.isEmpty()) "" else "$label\n"}" +
                "  input:   ${visible(input)}\n" +
                "  output:  ${visible(result.output)}\n" +
                "  policy:  $policy\n" +
                "  ${census.cardLengthRuns} card-length run(s), " +
                "${census.digitsOutsideRuns} digit(s) outside them\n" +
                "  ceiling $ceiling digits, $survived survived — ${survived - ceiling} too many",
            survived <= ceiling
        )
    }

    /** Renders the folded separators so a failure message is readable. */
    private fun visible(text: String): String = buildString {
        for (ch in text) {
            if (ch.code in 0x20..0x7E) append(ch) else append("<U+%04X>".format(ch.code))
        }
    }

    // ── The confirmed leaks, one test each ────────────────────────────────────

    @Test
    fun `an account number folded onto a PAN obeys the ceiling, for all 24 separators`() {
        for (separator in ALL_SEPARATORS) {
            val input = "Akaun 123456789012${separator}4111 1111 1111 1111"
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                assertCeiling(input, MaskPolicy('*', leading, trailing), "U+%04X".format(separator.code))
            }
        }
    }

    @Test
    fun `every adjacency of every operand obeys the ceiling, in both orders`() {
        for (separator in ALL_SEPARATORS) {
            for (input in adjacencyPairs(separator)) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    assertCeiling(input, MaskPolicy('*', leading, trailing), "U+%04X".format(separator.code))
                }
            }
        }
    }

    @Test
    fun `every sample obeys the ceiling under every separator`() {
        for (sample in SENSITIVE_SAMPLES) {
            for (separator in ALL_SEPARATORS) {
                val input = withSeparator(sample, separator)
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    assertCeiling(input, MaskPolicy('*', leading, trailing), "U+%04X".format(separator.code))
                }
            }
        }
    }

    @Test
    fun `trailing and leading prose does not lift the ceiling`() {
        val shapes = listOf(
            "RM 4222222222222%s4111 1111 1111 1111 terima kasih",
            "Akaun %s4111 1111 1111 1111",
            "GB29NWBK60161331926819%s4111 1111 1111 1111 sila semak",
            "880505-06-1234012-3456789%sterima kasih",
            "Invois 123456789012%s4111-1111-1111-1111."
        )
        for (shape in shapes) {
            for (separator in ALL_SEPARATORS) {
                val input = shape.format(separator)
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    assertCeiling(input, MaskPolicy('*', leading, trailing), "U+%04X".format(separator.code))
                }
            }
        }
    }

    @Test
    fun `the ceiling holds on the false-positive corpus too`() {
        for (line in FpCorpus.lines) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                assertCeiling(line, MaskPolicy('*', leading, trailing), line)
            }
        }
        assertCeiling(FpCorpus.joined, MaskPolicy('*', 0, MAX_REVEALED_DIGITS), "the corpus as one document")
    }

    @Test
    fun `the ceiling holds for every mask glyph, not just the star`() {
        val input = "Akaun 123456789012 4111 1111 1111 1111"
        for (glyph in MASK_GLYPHS) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                assertCeiling(input, MaskPolicy(glyph, leading, trailing), "glyph=$glyph")
            }
        }
    }

    @Test
    fun `the ceiling survives a fuzz over the characters that make runs`() {
        // Hand-written shapes only cover collisions somebody thought of, and the
        // two leaks that got through review were both shapes nobody had.
        //
        // Generated from *tokens* rather than characters, and that is what makes
        // it worth running: a per-character fuzz over this alphabet produces a
        // card-length run in well under one case in a hundred, so it spends its
        // whole budget on strings the property says nothing about. Stitching real
        // values and digit chunks together with the separators that fold puts a
        // run in most of them.
        val values = ADJACENCY_OPERANDS + listOf("Akaun", "RM", "terima kasih", "tel", ",", ". ")
        val random = Random(20260817)
        var withRuns = 0
        repeat(4_000) {
            val input = buildString {
                repeat(random.nextInt(2, 7)) {
                    when (random.nextInt(4)) {
                        0 -> append(values[random.nextInt(values.size)])
                        1 -> repeat(random.nextInt(1, 9)) { append('0' + random.nextInt(10)) }
                        2 -> append(ALL_SEPARATORS[random.nextInt(ALL_SEPARATORS.size)])
                        else -> append(values[random.nextInt(values.size)])
                    }
                }
            }
            if (census(input).cardLengthRuns > 0) withRuns++
            val (leading, trailing) = ALL_KEEP_COUNTS[random.nextInt(ALL_KEEP_COUNTS.size)]
            assertCeiling(input, MaskPolicy('*', leading, trailing), "fuzz")
        }
        assertTrue("only $withRuns of 4000 fuzz cases held a card-length run", withRuns >= 1_000)
    }

    // ── The sharp version, for the shapes where an exact statement is possible ──

    @Test
    fun `the PAN itself never survives any folded separator, at any slider position`() {
        val pan = "4111111111111111"
        for (separator in ALL_SEPARATORS) {
            for (
                input in listOf(
                    "Akaun 123456789012${separator}4111 1111 1111 1111",
                    "Akaun 4222222222222${separator}4111 1111 1111 1111",
                    "GB29NWBK60161331926819${separator}4111 1111 1111 1111",
                    "RM 4222222222222${separator}4111 1111 1111 1111 terima kasih",
                    "4111 1111 1111 1111${separator}123456789012"
                )
            ) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    val output = Redactor.redactAllInText(input, MaskPolicy('*', leading, trailing)).output
                    assertTrue(
                        "U+%04X keep=$leading/$trailing left the PAN in ${visible(output)}"
                            .format(separator.code),
                        !output.filter { it.isDigit() }.contains(pan)
                    )
                }
            }
        }
    }

    @Test
    fun `an IC running into a phone is one run, not an IC and a readable phone`() {
        // The reported shape. `MY_PHONE_REGEX` used to match `06-1234012` in the
        // middle of this, because its lookbehind accepted the dash that separates
        // the fields of an IC — so the engine claimed a phone number that was not
        // there, masked it to the phone rule's last three digits, and handed back
        // `880505-**-****012-3456789`: a date of birth and a whole real phone
        // number, at every slider position.
        for (input in listOf("880505-06-1234012-3456789", "IC880505-06-1234012-3456789")) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val result = Redactor.redactAllInText(input, MaskPolicy('*', leading, trailing))
                // Nothing here is a phone number, and claiming one is what made
                // the slider irrelevant to this input before: the phone rule
                // masks to its own fixed three digits whatever the slider says,
                // so a mis-claimed phone leaked the same digits at every position.
                assertTrue(
                    "keep=$leading/$trailing still read a phone out of it: ${result.counts}",
                    !result.counts.containsKey(SensitiveType.PHONE)
                )
                // The birth date leads the run, so LAST mode can never reach it.
                // FIRST mode can, and that is the slider being the slider — it is
                // asking for leading digits, the shipped masker reveals the same
                // ones, and the ceiling test above is what bounds how many.
                if (leading == 0) {
                    assertTrue(
                        "keep=$leading/$trailing kept the birth date: ${result.output}",
                        !result.output.contains("880505")
                    )
                }
            }
        }
        // At the position the app ships at, the trailing number goes too.
        assertTrue(
            Redactor.redactAllInText("880505-06-1234012-3456789", MaskPolicy('*')).output ==
                "******-**-*******-***6789"
        )
        // At the slider's ceiling it does not, and that is the licensed answer
        // rather than a residual: ten digits is what MAX_REVEALED_DIGITS permits,
        // the user dragged the control there, and the card-only masker reveals
        // those same ten plus three more. What the fix removed is this happening
        // at *every* position — which is what a mis-claimed phone number did,
        // because the phone rule ignores the slider by design.
        assertTrue(
            Redactor.redactAllInText("880505-06-1234012-3456789", MaskPolicy('*', 0, 10)).output ==
                "******-**-****012-3456789"
        )
    }

    @Test
    fun `a short run beside an IC is not left in the clear`() {
        // Eleven digits: below CARD's twelve-digit floor on its own, so no
        // detector ever offers a candidate for it — but it is part of a
        // twenty-three digit run, and the ceiling applies to the run.
        for ((leading, trailing) in ALL_KEEP_COUNTS) {
            val output = Redactor.redactAllInText(
                "901231-14-5678 41111111111",
                MaskPolicy('*', leading, trailing)
            ).output
            assertTrue(
                "keep=$leading/$trailing left the run readable: $output",
                !output.contains("41111111111")
            )
        }
    }
}
