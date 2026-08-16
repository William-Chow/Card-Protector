package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The Reveal control governs card numbers and nothing else, and the UI now says
 * so out loud. This is the mechanical half of that promise: for every non-card
 * sample, the output must be byte-identical across all eleven slider positions
 * and all three modes.
 *
 * It holds structurally rather than by discipline — [MaskPolicy] hands the keep
 * counts to the CARD detector alone — but it is exactly the kind of coupling
 * that gets reintroduced by a well-meaning "let the slider drive everything"
 * change, so it is pinned here.
 */
class SliderScopeTest {

    @Test
    fun `only card output moves when the slider moves`() {
        for ((type, sample) in TYPE_SAMPLES) {
            if (type == SensitiveType.CARD) continue
            val baseline = Redactor.redactAllInText(sample, MaskPolicy('*', 0, 4)).output
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                assertEquals(
                    "$type moved at keep=$leading/$trailing",
                    baseline,
                    Redactor.redactAllInText(sample, MaskPolicy('*', leading, trailing)).output
                )
            }
        }
    }

    @Test
    fun `card output still tracks the slider`() {
        val card = TYPE_SAMPLES.getValue(SensitiveType.CARD)
        assertEquals("************1111", Redactor.redactAllInText(card, MaskPolicy('*', 0, 4)).output)
        assertEquals("****************", Redactor.redactAllInText(card, MaskPolicy('*', 0, 0)).output)
        assertEquals("411111******1111", Redactor.redactAllInText(card, MaskPolicy('*', 6, 4)).output)
        assertNotEquals(
            Redactor.redactAllInText(card, MaskPolicy('*', 0, 0)).output,
            Redactor.redactAllInText(card, MaskPolicy('*', 0, 4)).output
        )
    }

    @Test
    fun `a mixed document only moves in its card`() {
        val text = "IC 901231-14-5678, card 4111111111111111, tel 012-3456789"
        val loose = Redactor.redactAllInText(text, MaskPolicy('*', 0, 4)).output
        val tight = Redactor.redactAllInText(text, MaskPolicy('*', 0, 0)).output
        assertEquals("IC ******-**-****, card ************1111, tel ***-****789", loose)
        assertEquals("IC ******-**-****, card ****************, tel ***-****789", tight)
    }
}
