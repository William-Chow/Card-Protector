package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The 1:1 property that the whole engine rests on. Detectors scan the normalized
 * string and masking splices back into the original, so if normalization ever
 * changed the length, every replacement offset past that point would be wrong —
 * and the failure would look like corrupted output, not like a crash.
 */
class NormalizeTest {

    private val mapped = listOf(
        '\uFF10', '\uFF15', '\uFF19',                     // full-width digits
        '\u0660', '\u0665', '\u0669',                     // Arabic-Indic digits
        '\u2010', '\u2013', '\u2015', '\u2212',           // dashes and the minus sign
        '\u00A0', '\u202F', '\u2002', '\u2007', '\u2009', // spaces
        '\u200B', '\u200C', '\u200D', '\uFEFF'            // zero-width and BOM
    )

    @Test
    fun `length is preserved for every mapped code point`() {
        for (ch in mapped) {
            assertEquals("$ch", 1, normalizeForScan(ch.toString()).length)
        }
    }

    @Test
    fun `length is preserved over a fuzz corpus`() {
        val alphabet = mapped + "abc 123-@./åé漢".toList()
        val random = Random(20260816)
        repeat(2_000) {
            val text = buildString {
                repeat(random.nextInt(0, 40)) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
            assertEquals("length moved for [$text]", text.length, normalizeForScan(text).length)
        }
    }

    @Test
    fun `digits and separators fold onto ASCII`() {
        assertEquals("0123456789", normalizeForScan("\uFF10\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18\uFF19"))
        assertEquals("0123456789", normalizeForScan("\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"))
        assertEquals("a-b-c-d", normalizeForScan("a\u2010b\u2013c\u2212d"))
        assertEquals("a b c", normalizeForScan("a\u00A0b\u2009c"))
    }

    @Test
    fun `ordinary ASCII is returned as the very same instance`() {
        val text = "nothing to fold here 1234"
        assertTrue(normalizeForScan(text) === text)
    }

    @Test
    fun `a full-width card number is still masked, and still masked as digits`() {
        // Sixteen digits, so it is a card rather than something with the shape
        // of a twelve-digit IC. `Char.isDigit()` is Unicode-aware, so masking
        // reads the original full-width run and counts every glyph as a digit.
        val fullWidth = "\uFF14" + "\uFF11".repeat(15)
        val output = Redactor.redactAllInText(fullWidth, MaskPolicy('*')).output
        assertEquals(fullWidth.length, output.length)
        assertEquals(12, maskCount(output, '*'))
        assertEquals(mapOf(SensitiveType.CARD to 1), Redactor.redactAllInText(fullWidth, MaskPolicy('*')).counts)
    }

    @Test
    fun `a zero-width space wedged into a card number does not hide it`() {
        val evaded = "4111\u200B111111111111"
        val output = Redactor.redactAllInText(evaded, MaskPolicy('*')).output
        assertTrue("evasion succeeded: $output", output.contains('*'))
        assertEquals(evaded.length, output.length)
    }
}
