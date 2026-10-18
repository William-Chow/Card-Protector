package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generalises the shipped card floor in `MaskingTest` to every type: no glyph,
 * no mode and no slider position may hand a sensitive value back intact, and the
 * types that promise to reveal nothing must reveal nothing.
 *
 * Every type × every mask mode × every slider position × every glyph the picker
 * offers — 8 × 3 × 11 × 10 combinations.
 */
class FloorTest {

    private fun eachCombination(body: (SensitiveType, String, Char, MaskPolicy) -> Unit) {
        for ((type, sample) in TYPE_SAMPLES) {
            for (glyph in MASK_GLYPHS) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    body(type, sample, glyph, MaskPolicy(glyph, leading, trailing))
                }
            }
        }
    }

    @Test
    fun `every sample is claimed exactly once, by the type that owns it`() {
        eachCombination { type, sample, _, policy ->
            assertEquals(
                "policy=$policy sample=$sample",
                mapOf(type to 1),
                Redactor.redactAllInText(sample, policy).counts
            )
        }
    }

    @Test
    fun `no combination ever returns the value unchanged`() {
        eachCombination { _, sample, glyph, policy ->
            val output = Redactor.redactAllInText(sample, policy).output
            assertTrue("policy=$policy leaked $sample", output != sample)
            assertTrue("policy=$policy masked nothing in $sample", maskCount(output, glyph) >= 1)
        }
    }

    @Test
    fun `an IC never reveals a digit, whatever the slider says`() {
        val sample = TYPE_SAMPLES.getValue(SensitiveType.MY_NRIC)
        for (glyph in MASK_GLYPHS) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val output = Redactor.redactAllInText(sample, MaskPolicy(glyph, leading, trailing)).output
                assertEquals("keep=$leading/$trailing on $sample", 0, digitCount(output))
                assertEquals("$glyph$glyph$glyph$glyph$glyph$glyph", output.take(6))
            }
        }
    }

    @Test
    fun `a secret keeps its brand marker and nothing else`() {
        val sample = TYPE_SAMPLES.getValue(SensitiveType.SECRET)
        for (glyph in MASK_GLYPHS) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val output = Redactor.redactAllInText(sample, MaskPolicy(glyph, leading, trailing)).output
                assertEquals("AKIA" + glyph.toString().repeat(8), output)
            }
        }
    }

    @Test
    fun `a URL keeps no character of its path, query value or fragment`() {
        val sample = "https://drive.google.com/file/d/1a2B3c/view?usp=sharing#page2"
        for (glyph in MASK_GLYPHS) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val output = Redactor.redactAllInText(sample, MaskPolicy(glyph, leading, trailing)).output
                for (body in listOf("file", "1a2B3c", "view", "sharing", "page2")) {
                    assertFalse("keep=$leading/$trailing left $body in $output", output.contains(body))
                }
                assertTrue("host was lost: $output", output.startsWith("https://drive.google.com/"))
                assertTrue("query key was lost: $output", output.contains("usp="))
            }
        }
    }

    @Test
    fun `a PAN a URL host or an email domain keeps verbatim is hidden in any digit system`() {
        // The floor used to match the raw replacement with CARD_REGEX's
        // ASCII-only `\d`, so a full-width PAN in a host came back with ten
        // digits showing — stopped by the run ceiling, not by the floor.
        for (zero in listOf(0x30, 0xFF10, 0x0660, 0x06F0, 0x0966) + SUPPLEMENTARY_ZEROS) {
            val pan = inDigits("4111111111111111", zero)
            for (sample in listOf("https://$pan.example.com/x", "a@$pan.com")) {
                for (glyph in MASK_GLYPHS) {
                    val result = Redactor.redactAllInText(sample, MaskPolicy(glyph))
                    assertEquals("$sample -> ${result.output}", 0, digitCount(result.output))
                    assertTrue("$sample gave ${result.counts}", result.counts.containsKey(SensitiveType.CARD))
                    assertTrue(result.output, isWellFormed(result.output))
                }
            }
        }
    }

    @Test
    fun `a phone never reveals more than three digits of its national body`() {
        for (sample in listOf("+60 12-345 6789", "Tel: 03-1234 5678", "012-3456789", "+8613800138000")) {
            for (glyph in MASK_GLYPHS) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    val output = Redactor.redactAllInText(sample, MaskPolicy(glyph, leading, trailing)).output
                    // `+60` is a country code, kept verbatim by design; everything
                    // after it is the national body and is capped at three digits.
                    val body = if (output.contains("+60")) output.substringAfter("+60") else output
                    assertTrue(
                        "keep=$leading/$trailing revealed ${digitCount(body)} digits in $output",
                        digitCount(body) <= 3
                    )
                }
            }
        }
    }

    @Test
    fun `an IBAN never reveals more than the country code and four characters`() {
        for (sample in listOf("MT84MALT011000012345MTLCAST001S", "GB29 NWBK 60161331926819")) {
            for (glyph in MASK_GLYPHS) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    val output = Redactor.redactAllInText(sample, MaskPolicy(glyph, leading, trailing)).output
                    val revealed = output.count { it.isLetterOrDigit() && it != glyph }
                    assertTrue("keep=$leading/$trailing revealed $revealed of $output", revealed <= 6)
                }
            }
        }
    }

    @Test
    fun `values written in two-Char digits keep the floors their types promise`() {
        for (zero in SUPPLEMENTARY_ZEROS) {
            for (glyph in MASK_GLYPHS) {
                // An IC reveals nothing, whatever the slider says.
                val ic = Redactor.redactAllInText(inDigits("901231-14-5678", zero), MaskPolicy(glyph, 0, 10))
                assertEquals(mapOf(SensitiveType.MY_NRIC to 1), ic.counts)
                assertEquals(ic.output, 0, digitCount(ic.output))
                // A phone keeps its +60 whole, and at most three digits after it.
                val phone = Redactor.redactAllInText(inDigits("+60 12-345 6789", zero), MaskPolicy(glyph, 0, 10))
                val prefix = inDigits("+60", zero)
                assertTrue(phone.output, phone.output.startsWith(prefix))
                assertTrue(phone.output, digitCount(phone.output.removePrefix(prefix)) <= 3)
                // An email whose local part is a PAN reveals none of it.
                val email = Redactor.redactAllInText(inDigits("4111111111111111", zero) + "@x.com", MaskPolicy(glyph))
                assertEquals(email.output, 0, digitCount(email.output))
                for (output in listOf(ic.output, phone.output, email.output)) assertTrue(output, isWellFormed(output))
            }
        }
    }
}
