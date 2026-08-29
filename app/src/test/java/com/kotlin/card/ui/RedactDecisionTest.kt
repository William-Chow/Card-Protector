package com.kotlin.card.ui

import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PROCESS_TEXT sheet's decisions.
 *
 * These are the choices with real consequences: offering Replace when the host
 * cannot accept one silently does nothing, and offering it for a payload too
 * large for a Binder transaction throws inside the *other* app. Both are
 * unit-testable because [decideRedaction] is pure; only the button tap itself
 * needs an instrumented test, and that is the thin part.
 */
class RedactDecisionTest {

    private val policy = MaskPolicy('*', 0, 4)

    private fun decide(selection: String, readOnly: Boolean = false) =
        decideRedaction(selection, readOnly, policy)

    @Test
    fun `a selection with a card offers replacement`() {
        val decision = decide("pay 4111111111111111 now")
        assertTrue(decision.found)
        assertTrue(decision.canReplace)
        assertTrue(decision.canCopy)
        assertEquals("pay ************1111 now", decision.result.output)
    }

    @Test
    fun `a read-only host never offers replacement but still offers copy`() {
        val decision = decide("pay 4111111111111111 now", readOnly = true)
        assertTrue(decision.found)
        assertFalse("replacing into a read-only field silently does nothing", decision.canReplace)
        assertTrue(decision.canCopy)
    }

    @Test
    fun `text with nothing sensitive offers no replacement`() {
        val decision = decide("Meeting at 14:30 in Room 2.4")
        assertFalse(decision.found)
        assertFalse(decision.canReplace)
        // Copy stays available: the user may still want the text back verbatim.
        assertTrue(decision.canCopy)
        assertEquals("Meeting at 14:30 in Room 2.4", decision.result.output)
    }

    @Test
    fun `an oversized selection is refused without being scanned`() {
        val huge = "4111111111111111 ".repeat(MAX_INBOUND_CHARS / 10)
        assertTrue(huge.length > MAX_INBOUND_CHARS)
        val decision = decide(huge)
        assertTrue(decision.oversized)
        assertFalse(decision.canReplace)
        assertFalse(decision.canCopy)
        // Refused means untouched, not half-masked.
        assertEquals(huge, decision.result.output)
        assertTrue(decision.result.counts.isEmpty())
    }

    @Test
    fun `a selection just under the inbound cap is still scanned`() {
        val body = "4111111111111111 "
        val text = body.repeat(MAX_INBOUND_CHARS / body.length / 2)
        val decision = decide(text)
        assertFalse(decision.oversized)
        assertTrue(decision.found)
        assertTrue(decision.canReplace)
        assertFalse("masking here is 1:1, so it cannot cross the replace cap", decision.tooLongToReplace)
    }

    @Test
    fun `the sheet honours the stored mask glyph and card reveal`() {
        // What makes the sheet feel like the same app as the main screen.
        val decision = decideRedaction("4111111111111111", false, MaskPolicy('#', 0, 0))
        assertEquals("################", decision.result.output)
    }

    @Test
    fun `the sheet applies the full multi-type engine, not just card masking`() {
        val decision = decide("IC 901231-14-5678, call 012-345 6789")
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.PHONE to 1),
            decision.result.counts
        )
        assertEquals("IC ******-**-****, call ***-*** *789", decision.result.output)
    }
}
