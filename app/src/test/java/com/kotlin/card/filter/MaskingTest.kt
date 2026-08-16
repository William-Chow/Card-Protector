package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Masking rules, with the privacy floor front and centre: no slider position,
 * mode, or caller may ever hand back an unmasked number.
 */
class MaskingTest {

    private val visa16 = "4111111111111111"
    private val amex15 = "378282246310005"

    // ── The floor ────────────────────────────────────────────────────────────

    @Test
    fun `slider at its ceiling never reveals a whole 16-digit card`() {
        val (leading, trailing) = keepCounts(MaskMode.LAST, MAX_REVEALED_DIGITS)
        val masked = maskNumber(visa16, '*', leading, trailing)
        assertEquals("******1111111111", masked)
        assertEquals(6, masked.count { it == '*' })
    }

    @Test
    fun `an over-large keep count is clamped, not honoured`() {
        // The old slider went to 16 and this returned the card verbatim.
        assertEquals("******1111111111", maskNumber(visa16, '*', 0, 16))
        assertEquals("4111111111******", maskNumber(visa16, '*', 16, 0))
        assertEquals("******1111111111", maskNumber(visa16, '*', 0, Int.MAX_VALUE))
    }

    @Test
    fun `at least one digit stays masked for every length 1 to 19`() {
        for (length in 1..19) {
            val account = "4".repeat(length)
            for (keepN in 0..MAX_REVEALED_DIGITS) {
                for (mode in MaskMode.entries) {
                    val (leading, trailing) = keepCounts(mode, keepN)
                    val masked = maskNumber(account, '*', leading, trailing)
                    assertTrue(
                        "len=$length mode=$mode keepN=$keepN produced $masked",
                        masked.count { it == '*' } >= 1
                    )
                    assertTrue(
                        "len=$length mode=$mode keepN=$keepN leaked the number",
                        masked != account
                    )
                }
            }
        }
    }

    @Test
    fun `a 16-digit card always keeps at least six digits masked`() {
        for (keepN in 0..MAX_REVEALED_DIGITS) {
            for (mode in MaskMode.entries) {
                val (leading, trailing) = keepCounts(mode, keepN)
                val masked = maskNumber(visa16, '*', leading, trailing)
                assertTrue(
                    "mode=$mode keepN=$keepN produced $masked",
                    masked.count { it == '*' } >= 6
                )
            }
        }
    }

    @Test
    fun `the clamp surrenders BIN-side digits first`() {
        // Ten digits of headroom on a 16-digit card: the tail survives intact and
        // the leading request absorbs the whole reduction.
        assertEquals(6 to 4, clampKeepCounts(digitCount = 16, keepLeading = 8, keepTrailing = 4))
        assertEquals(4 to 6, clampKeepCounts(digitCount = 16, keepLeading = 9, keepTrailing = 6))
        assertEquals(0 to 10, clampKeepCounts(digitCount = 16, keepLeading = 6, keepTrailing = 16))
    }

    @Test
    fun `negative keep counts are treated as zero`() {
        assertEquals(0 to 0, clampKeepCounts(digitCount = 16, keepLeading = -3, keepTrailing = -1))
        assertEquals("****************", maskNumber(visa16, '*', -3, -1))
    }

    @Test
    fun `short inputs still lose a digit`() {
        assertEquals("*", maskNumber("7", '*', 4, 4))
        assertEquals("*234", maskNumber("1234", '*', 0, 9))
        assertEquals("1*", maskNumber("12", '*', 2, 0))
    }

    // ── Existing behaviour the clamp must not disturb ─────────────────────────

    @Test
    fun `default last-4 masking is unchanged`() {
        val (leading, trailing) = keepCounts(MaskMode.LAST, 4)
        assertEquals("************1111", maskNumber(visa16, '*', leading, trailing))
    }

    @Test
    fun `first6 last4 preset is exact for real card lengths`() {
        val (leading, trailing) = keepCounts(MaskMode.FIRST6_LAST4, 4)
        assertEquals("411111******1111", maskNumber(visa16, '*', leading, trailing))
        assertEquals("378282*****0005", maskNumber(amex15, '*', leading, trailing))
        // The regex floor is 12 digits, and 6 + 4 fits under the ceiling there too.
        assertEquals("123456**7890", maskNumber("123456787890", '*', leading, trailing))
    }

    @Test
    fun `separators are preserved and do not count as digits`() {
        assertEquals("4111 11** **** 1111", maskNumber("4111 1111 1111 1111", '*', 6, 4))
        assertEquals("4111-11**-****-1111", maskNumber("4111-1111-1111-1111", '*', 6, 4))
    }

    @Test
    fun `text with no digits is returned untouched`() {
        assertEquals("no card here", maskNumber("no card here", '*', 4, 4))
    }

    @Test
    fun `the mask symbol is honoured`() {
        assertEquals("••••••1111111111", maskNumber(visa16, '•', 0, 16))
    }

    // ── Batch path ───────────────────────────────────────────────────────────

    @Test
    fun `batch masking clamps every card independently`() {
        val text = "pay 4111111111111111 or 378282246310005 today"
        val masked = maskAllInText(text, '*', 0, MAX_REVEALED_DIGITS)
        assertEquals("pay ******1111111111 or *****2246310005 today", masked)
    }

    @Test
    fun `batch masking leaves surrounding text alone`() {
        val masked = maskAllInText("ref 42 card 4111111111111111", '*', 0, 4)
        assertEquals("ref 42 card ************1111", masked)
    }

    @Test
    fun `countCards finds card-like runs only`() {
        assertEquals(0, countCards("1234"))
        assertEquals(1, countCards("4111 1111 1111 1111"))
        assertEquals(2, countCards("4111111111111111 and 378282246310005"))
    }

    @Test
    fun `extractFirstCardDigits strips separators and caps at 19`() {
        assertEquals("4111111111111111", extractFirstCardDigits("card: 4111-1111-1111-1111!"))
        assertEquals("1234567890123456789", extractFirstCardDigits("1234567890123456789012"))
    }
}
