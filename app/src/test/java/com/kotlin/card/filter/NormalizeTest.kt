package com.kotlin.card.filter

import com.kotlin.card.data.MASK_SYMBOLS
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
        '\u200B', '\u200C', '\u200D', '\uFEFF',           // zero-width and BOM
        '\u06F4', '\u096A', '\u09E6', '\u0E51',           // Persian, Devanagari, Bengali, Thai digits
        '\u3000', '\u205F', '\uFF0D', '\uFE63', '\u301C', // ideographic space, more dashes
        '\u00AD', '\u2060', '\u200E', '\u202A', '\u2066'  // soft hyphen, word joiner, bidi marks
    )

    /** The zero of every digit system the evasion test writes a card in. */
    private val digitZeros = listOf(
        '0',      // ASCII
        '\uFF10', // full-width
        '\u0660', // Arabic-Indic
        '\u06F0', // Persian
        '\u0966', // Devanagari
        '\u09E6', // Bengali
        '\u0E50'  // Thai
    )

    /** [template] with each ASCII digit written from [zero] and each space as [separator]. */
    private fun write(template: String, zero: Char, separator: Char): String =
        buildString(template.length) {
            for (ch in template) {
                append(
                    when (ch) {
                        in '0'..'9' -> zero + (ch - '0')
                        ' ' -> separator
                        else -> ch
                    }
                )
            }
        }

    private fun hex(ch: Char): String = "U+%04X".format(ch.code)

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

    @Test
    fun `every Unicode decimal digit folds onto its ASCII value`() {
        for (ch in '\u0000'..'\uFFFF') {
            if (!ch.isDigit()) continue
            assertEquals(hex(ch), ('0' + ch.digitToInt()).toString(), normalizeForScan(ch.toString()))
        }
    }

    @Test
    fun `nothing in the BMP folds except digits and FOLDED_SEPARATORS`() {
        // The other half of the contract with Samples.kt: every adjacency suite
        // runs over FOLDED_SEPARATORS, so a character folded onto a space or a
        // dash that is missing from it is a separator nothing tests. That is how
        // the ideographic space and the soft hyphen shipped as bypasses.
        val separators = FOLDED_SEPARATORS.toSet()
        for (ch in '\u0000'..'\uFFFF') {
            val folded = normalizeForScan(ch.toString())
            assertEquals(hex(ch), 1, folded.length)
            when {
                ch.isDigit() -> continue
                ch in separators -> assertTrue("${hex(ch)} -> $folded", folded == " " || folded == "-")
                else -> assertEquals("${hex(ch)} folded but is not in FOLDED_SEPARATORS", ch.toString(), folded)
            }
        }
    }

    @Test
    fun `no mask glyph folds, so a mask can only ever break a run`() {
        // cardFloor and the run ceiling both scan text that already holds mask
        // glyphs. A glyph that folded into a digit, a space or a dash could
        // stitch two runs into one that neither of them ever measured.
        for (glyph in MASK_SYMBOLS) {
            assertEquals("$glyph", glyph.toString(), normalizeForScan(glyph.toString()))
        }
    }

    @Test
    fun `a card is masked in every digit system, across every separator`() {
        // Each of these came back whole, with nothing in the counts: full-width
        // digits split by the ideographic space a CJK keyboard types, a soft
        // hyphen or a word joiner wedged between groups, Persian or Devanagari
        // digits.
        for (zero in digitZeros) {
            for (separator in ALL_SEPARATORS) {
                val card = write("4111 1111 1111 1111", zero, separator)
                val result = Redactor.redactAllInText(card, MaskPolicy('*'))
                val label = "${hex(zero)} / ${hex(separator)}"
                assertEquals(label, write("**** **** **** 1111", zero, separator), result.output)
                assertEquals(label, mapOf(SensitiveType.CARD to 1), result.counts)
            }
        }
    }
}
