package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The change is never less masking than before" — with the one exception named
 * exactly, and gated at every slider position rather than only the comfortable
 * ones.
 *
 * On ordinary text the property is exact equality, and that is the strongest and
 * most important form of it — see [RedactorParityTest]. On sensitive text it
 * holds everywhere except a single, bounded gap: **every non-card type carries a
 * fixed reveal floor, and where the Reveal slider is dragged below that floor,
 * the type keeps a few characters the card-only masker would have hidden.**
 * PHONE keeps the last three digits of the national body; IBAN keeps the last
 * four alphanumerics. Both are independent of the slider by design — that is
 * what [SliderScopeTest] pins — so dragging the card slider to zero cannot pull
 * them down with it, and coupling them back together to rescue the arithmetic
 * would break the more valuable property.
 *
 * **What this KDoc used to say was wrong, and worth spelling out.** It claimed
 * the property held "at every slider position the app actually ships at", with
 * the only counter-example at keepN 0–2 on a lone international number. It did
 * not hold at the *default* position: two sensitive values written one space or
 * one dash apart formed a single card run, the card match was dropped whole for
 * overlapping the first value's claim, and a full PAN came back unmasked — at
 * every slider position, in both orders. That was a live leak, not a rounding
 * error in a claim, and it survived because the samples below held exactly one
 * sensitive value each and the sweep skipped keepN 0–2. Both holes are closed
 * here; [AdjacencyLeakTest] is the dedicated gate.
 */
class MonotonicityTest {

    /**
     * Characters a type keeps whatever the slider says, and therefore the exact
     * budget by which it may exceed the card-only engine at a low slider
     * position. Anything not listed keeps nothing the card masker hid — a type
     * that starts to needs a line here, and a reason.
     */
    private val FIXED_FLOOR: Map<SensitiveType, Int> = mapOf(
        SensitiveType.PHONE to 3,
        SensitiveType.IBAN to 4
    )

    private fun floorBudget(counts: Map<SensitiveType, Int>): Int =
        counts.entries.sumOf { (type, found) -> found * (FIXED_FLOOR[type] ?: 0) }

    @Test
    fun `ordinary text is masked exactly as much as before, everywhere on the slider`() {
        for (line in FpCorpus.lines) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val before = maskAllInText(line, '*', leading, trailing)
                val after = Redactor.redactAllInText(line, '*', leading, trailing).output
                assertEquals("keep=$leading/$trailing on $line", digitCount(before), digitCount(after))
                assertEquals(maskCount(before, '*'), maskCount(after, '*'))
            }
        }
    }

    @Test
    fun `sensitive text never keeps more digits than the card-only engine did`() {
        for (sample in SENSITIVE_SAMPLES) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val before = digitCount(maskAllInText(sample, '*', leading, trailing))
                val result = Redactor.redactAllInText(sample, '*', leading, trailing)
                val after = digitCount(result.output)
                val budget = floorBudget(result.counts)
                assertTrue(
                    "keep=$leading/$trailing on $sample\n" +
                        "  card-only: ${maskAllInText(sample, '*', leading, trailing)} ($before digits)\n" +
                        "  engine:    ${result.output} ($after digits, budget $budget, ${result.counts})",
                    after <= before + budget
                )
            }
        }
    }

    @Test
    fun `no sample is over budget at the positions the app ships at`() {
        // Above keepN 3 the fixed floors cannot bind, so the property is exact
        // there with no budget at all. This is the half a user actually sees.
        val shipped = MaskMode.entries.flatMap { mode -> (3..MAX_REVEALED_DIGITS).map { keepCounts(mode, it) } }
        for (sample in SENSITIVE_SAMPLES) {
            for ((leading, trailing) in shipped) {
                val before = digitCount(maskAllInText(sample, '*', leading, trailing))
                val after = digitCount(Redactor.redactAllInText(sample, '*', leading, trailing).output)
                assertTrue("keep=$leading/$trailing on $sample kept $after digits, was $before", after <= before)
            }
        }
    }

    @Test
    fun `every type that used to be masked as a card is still masked`() {
        // The reclassifications: each of these matched the card pattern before
        // and is now claimed by a detector that understands it. None of them may
        // come back readable.
        for (sample in listOf(
            "901231-14-5678",
            "901231145678",
            "MT84MALT011000012345MTLCAST001S",
            "GB29 NWBK 60161331926819",
            "+8613800138000"
        )) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val output = Redactor.redactAllInText(sample, '*', leading, trailing).output
                assertTrue("$sample survived keep=$leading/$trailing", output != sample)
                assertTrue("$sample lost its mask at keep=$leading/$trailing", output.contains('*'))
            }
        }
    }

    @Test
    fun `the documented counter-example is exactly where we think it is`() {
        // Card slider at zero: the old engine masked all thirteen digits of the
        // run; the new one reads it as a phone number and keeps the last three.
        // Documented, deliberate, and pinned so it cannot drift unnoticed — and
        // three digits is exactly the budget PHONE declares above.
        assertEquals("+*************", maskAllInText("+8613800138000", '*', 0, 0))
        assertEquals("+**********000", Redactor.redactAllInText("+8613800138000", '*', 0, 0).output)
    }

    @Test
    fun `the budget is never spent by a type that has no floor`() {
        // A card next to an IC or an IBAN has no budget at all: those types
        // reveal nothing a card run could have covered, so the comparison there
        // is exact at every slider position, including zero.
        for (sample in listOf(
            "901231-14-5678 4111111111111111",
            "4111111111111111 901231-14-5678",
            "901231-14-5678-4111111111111111",
            "Ali 901231-14-5678 4111 1111 1111 1111"
        )) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val before = digitCount(maskAllInText(sample, '*', leading, trailing))
                val after = digitCount(Redactor.redactAllInText(sample, '*', leading, trailing).output)
                assertTrue("keep=$leading/$trailing on $sample kept $after digits, was $before", after <= before)
            }
        }
    }
}
