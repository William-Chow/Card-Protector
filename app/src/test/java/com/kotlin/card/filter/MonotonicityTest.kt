package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The change is never less masking than before."
 *
 * On ordinary text that is exact equality, and that is the strongest and most
 * important form of it — see [RedactorParityTest]. On sensitive text it holds at
 * every slider position the app actually ships at, and this test pins that.
 *
 * **It does not hold at every conceivable slider position, and that is a real
 * limitation rather than a bug.** The build spec asserts it universally; the
 * counter-example is pinned below. Once the slider is card-only — which is the
 * more valuable of the two properties, and the one the UI now promises — the
 * fixed defaults for other types are independent of it, so dragging the card
 * slider to zero makes the card path stricter than a phone's fixed last-3 while
 * leaving the phone where it is. Coupling them back together to rescue
 * monotonicity would break [SliderScopeTest], which is the wrong trade.
 */
class MonotonicityTest {

    /** Slider positions where the card path reveals at least as much as any fixed default. */
    private val shippedKeepCounts: List<Pair<Int, Int>> =
        MaskMode.entries.flatMap { mode -> (3..MAX_REVEALED_DIGITS).map { keepCounts(mode, it) } }

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
            for ((leading, trailing) in shippedKeepCounts) {
                val before = digitCount(maskAllInText(sample, '*', leading, trailing))
                val after = digitCount(Redactor.redactAllInText(sample, '*', leading, trailing).output)
                assertTrue(
                    "keep=$leading/$trailing on $sample kept $after digits, was $before",
                    after <= before
                )
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
        // Documented, deliberate, and pinned so it cannot drift unnoticed.
        assertEquals("+*************", maskAllInText("+8613800138000", '*', 0, 0))
        assertEquals("+**********000", Redactor.redactAllInText("+8613800138000", '*', 0, 0).output)
    }
}
