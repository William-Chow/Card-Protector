package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sensitive values written in styled or full-width copies of ASCII: the bold
 * and monospace alphabets a "fancy text" generator produces, the full-width
 * forms a CJK keyboard types, and superscript and circled digits.
 *
 * Each of these used to come back whole. The scanner did not fold the letters
 * at all, and where it did fold a character the masker did not always agree
 * that it was a digit, so a detected number could still be handed back.
 */
class StyledTextTest {

    /** [text] with its ASCII letters and digits moved into the alphabets that start at these code points. */
    private fun styled(text: String, upperA: Int, lowerA: Int, zero: Int): String = buildString {
        for (ch in text) {
            when (ch) {
                in 'A'..'Z' -> appendCodePoint(upperA + (ch - 'A'))
                in 'a'..'z' -> appendCodePoint(lowerA + (ch - 'a'))
                in '0'..'9' -> appendCodePoint(zero + (ch - '0'))
                else -> append(ch)
            }
        }
    }

    /** Mathematical bold: letters and digits change, punctuation stays ASCII. */
    private fun bold(text: String) = styled(text, 0x1D400, 0x1D41A, 0x1D7CE)

    /** Full-width: letters, digits and ASCII punctuation all move to U+FF01..U+FF5E. */
    private fun fullWidth(text: String) = buildString {
        for (ch in text) if (ch in '!'..'~') appendCodePoint(ch.code - 0x21 + 0xFF01) else append(ch)
    }

    /** Superscript digits, which Unicode does not count as decimal digits. */
    private fun superscript(text: String) = buildString {
        val digits = intArrayOf(0x2070, 0xB9, 0xB2, 0xB3, 0x2074, 0x2075, 0x2076, 0x2077, 0x2078, 0x2079)
        for (ch in text) if (ch in '0'..'9') appendCodePoint(digits[ch - '0']) else append(ch)
    }

    /** Circled digits, which Unicode does not count as decimal digits either. */
    private fun circled(text: String) = buildString {
        for (ch in text) {
            when (ch) {
                '0' -> appendCodePoint(0x24EA)
                in '1'..'9' -> appendCodePoint(0x2460 + (ch - '1'))
                else -> append(ch)
            }
        }
    }

    private fun redact(text: String, policy: MaskPolicy = MaskPolicy('*')): RedactResult {
        val result = Redactor.redactAllInText(text, policy)
        assertTrue("not valid UTF-16: ${result.output}", isWellFormed(result.output))
        return result
    }

    @Test
    fun `a link typed full-width keeps its host and loses its path, query value and fragment`() {
        val result = redact(fullWidth("https://example.com/account/12345?usp=sharing#top"))
        assertEquals(mapOf(SensitiveType.URL to 1), result.counts)
        assertEquals(
            fullWidth("https://example.com/") + "****" + fullWidth("/") + "****" +
                fullWidth("?usp=") + "****" + fullWidth("#") + "****",
            result.output
        )
    }

    @Test
    fun `a link in CJK prose ends at the full-width bracket that closes it`() {
        // The bracket used to be part of the link, so the prose after it was
        // masked as a path segment.
        val result = redact("见https://x.com/a）谢谢")
        assertEquals("见https://x.com/****）谢谢", result.output)
    }

    @Test
    fun `an email in bold letters is found, and its initial is kept whole`() {
        val result = redact(bold("siti.aminah@sekolah.edu.my"))
        assertEquals(mapOf(SensitiveType.EMAIL to 1), result.counts)
        assertEquals(bold("s") + "*".repeat(10) + bold("@sekolah.edu.my"), result.output)
    }

    @Test
    fun `an email written full-width is masked rather than handed back`() {
        val result = redact(fullWidth("a.b@example.com"))
        assertEquals(mapOf(SensitiveType.EMAIL to 1), result.counts)
        assertEquals(fullWidth("a") + "**" + fullWidth("@example.com"), result.output)
    }

    @Test
    fun `an API key in bold letters is still a secret`() {
        val result = redact(bold("AKIAIOSFODNN7EXAMPLE"))
        assertEquals(mapOf(SensitiveType.SECRET to 1), result.counts)
        assertEquals(bold("AKIA") + "********", result.output)
    }

    @Test
    fun `a password after a full-width colon is masked`() {
        val result = redact("password" + fullWidth(":") + " hunter22")
        assertEquals(mapOf(SensitiveType.SECRET to 1), result.counts)
        assertEquals("password" + fullWidth(":") + " ********", result.output)
    }

    @Test
    fun `a full-width IBAN reveals no more than its country code and last four`() {
        val iban = fullWidth("GB29NWBK60161331926819")
        for (glyph in MASK_GLYPHS) {
            val result = redact(iban, MaskPolicy(glyph))
            assertEquals(mapOf(SensitiveType.IBAN to 1), result.counts)
            assertTrue(result.output, result.output.startsWith(fullWidth("GB")))
            assertTrue(result.output, result.output.endsWith(fullWidth("6819")))
            assertEquals(result.output, 6, result.output.count { it != glyph })
        }
    }

    @Test
    fun `a full-width IP address keeps only its first octet`() {
        val result = redact(fullWidth("203.0.113.45"))
        assertEquals(mapOf(SensitiveType.IPV4 to 1), result.counts)
        assertEquals(fullWidth("203.") + "***" + fullWidth(".") + "***" + fullWidth(".") + "***", result.output)
    }

    @Test
    fun `a card in superscript or circled digits is masked like any other card`() {
        for (write in listOf(::superscript, ::circled)) {
            val result = redact(write("4111 1111 1111 1111"))
            assertEquals(mapOf(SensitiveType.CARD to 1), result.counts)
            assertEquals(write("**** **** **** 1111"), result.output)
        }
    }

    @Test
    fun `styled text that hides nothing is left alone`() {
        val text = bold("Hello, see you at 5 tomorrow") + " " + fullWidth("(ok)")
        val result = redact(text)
        assertEquals(emptyMap<SensitiveType, Int>(), result.counts)
        assertEquals(text, result.output)
    }
}
