package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expiry dates and CVVs: the two fields printed on the card this app is named
 * after, and the two it could not read until now.
 *
 * The gap they close is not academic. A card block pasted out of an email came
 * back with the PAN masked and `Exp 12/26 CVV 123` sitting underneath it in
 * plain text — every remaining field of a card-not-present transaction, on
 * output the user had been told was safe to send. The counts said "1 card
 * found", which is the part that makes it worse than a miss: the sheet offered
 * "Replace selection" and the user had no reason to look.
 *
 * Both types reveal nothing at any slider position, so the tests below pin the
 * exact output rather than a digit budget.
 */
class CardFieldsTest {

    private fun redact(text: String) = Redactor.redactAllInText(text, MaskPolicy('*')).output

    private fun counts(text: String) = Redactor.redactAllInText(text, MaskPolicy('*')).counts

    // ── The shape the app exists for ──────────────────────────────────────────

    @Test
    fun `a full card block loses every field`() {
        assertEquals(
            "Card ************1111 Exp **/** CVV ***",
            redact("Card 4111111111111111 Exp 12/26 CVV 123")
        )
        assertEquals(
            mapOf(
                SensitiveType.CARD_EXPIRY to 1,
                SensitiveType.CVV to 1,
                SensitiveType.CARD to 1
            ),
            counts("Card 4111111111111111 Exp 12/26 CVV 123")
        )
    }

    @Test
    fun `a card block written across lines loses every field`() {
        assertEquals(
            "Kad ************1111\nLuput: **/****\nCVV ****",
            redact("Kad 4111111111111111\nLuput: 09/2027\nCVV 4321")
        )
    }

    @Test
    fun `the summary names the fields it hid`() {
        assertEquals(
            "1 expiry date · 1 CVV · 1 card",
            summarizeCounts(counts("Card 4111111111111111 Exp 12/26 CVV 123"))
        )
    }

    // ── Expiry: the label anchor ──────────────────────────────────────────────

    @Test
    fun `every expiry label anchors an otherwise bare date`() {
        for (label in listOf(
            "Exp", "exp", "EXP", "Exp date", "Expiry", "Expiry date", "Expires",
            "Expiration", "Expiration date", "Valid thru", "Valid through",
            "Valid to", "Valid until", "Good thru", "Luput", "Tarikh luput",
            "Sah hingga", "Tamat tempoh"
        )) {
            for (separator in listOf(" ", ": ", " : ", "= ", "  ")) {
                val text = "$label$separator" + "12/26"
                assertEquals(
                    "\"$text\" was not read as an expiry",
                    mapOf(SensitiveType.CARD_EXPIRY to 1),
                    counts(text)
                )
            }
        }
    }

    @Test
    fun `a label carries the dash form too`() {
        assertEquals(mapOf(SensitiveType.CARD_EXPIRY to 1), counts("Expiry 12-26"))
        assertEquals("Expiry **-**", redact("Expiry 12-26"))
    }

    @Test
    fun `a label must be a whole word`() {
        // "unexp" ends in "exp" and is not a label. The boundary check in
        // expiryLabelled is what refuses it.
        assertEquals(emptyMap<SensitiveType, Int>(), counts("unexp 12/26"))
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Xexpiry 12/26"))
    }

    @Test
    fun `all four year and separator forms are accepted behind a label`() {
        assertEquals("Exp **/**", redact("Exp 12/26"))
        assertEquals("Exp **/****", redact("Exp 12/2026"))
        assertEquals("Exp **-**", redact("Exp 12-26"))
        assertEquals("Exp **-****", redact("Exp 12-2026"))
    }

    // ── Expiry: the card anchor ───────────────────────────────────────────────

    @Test
    fun `an unlabelled expiry is claimed when a card shares its line`() {
        assertEquals(
            "************1111 **/**",
            redact("4111111111111111 12/26")
        )
        assertEquals(
            "**/** ************1111",
            redact("12/26 4111111111111111")
        )
    }

    @Test
    fun `the card anchor does not reach across a line`() {
        // Named false negative, pinned so it cannot change silently: an
        // unlabelled expiry on its own line is not claimed. A window that
        // reached the next line would turn every MM/YY in a long paste into an
        // expiry the moment one card appeared anywhere in it.
        assertEquals(
            mapOf(SensitiveType.CARD to 1),
            counts("4111111111111111\n12/26")
        )
    }

    @Test
    fun `the card anchor takes only the slash form`() {
        // "items 12-26" beside an account number is a range far more often than
        // it is an expiry, so circumstantial evidence buys only the shape a card
        // is actually printed in.
        assertEquals(mapOf(SensitiveType.CARD to 1), counts("Order 123456789012 items 12-26"))
        assertEquals(
            mapOf(SensitiveType.CARD_EXPIRY to 1, SensitiveType.CARD to 1),
            counts("Order 123456789012 items 12/26")
        )
    }

    // ── Expiry: the shapes that must never match ──────────────────────────────

    @Test
    fun `a full date cannot produce an expiry at either seam`() {
        // This is the one that matters most in this market: DD/MM/YYYY is the
        // standard written form, and both of its seams are pinned by a slash.
        for (date in listOf("16/08/2026", "01/12/25", "2026-08-16", "16-08-2026")) {
            assertEquals("$date was read as an expiry", emptyMap<SensitiveType, Int>(), counts("Exp $date"))
        }
    }

    @Test
    fun `a single-digit month is never an expiry`() {
        for (text in listOf("Exp 1/2", "Exp 3/4", "Exp 9/26", "SS15/4B")) {
            assertFalse(
                "$text was read as an expiry",
                counts(text).containsKey(SensitiveType.CARD_EXPIRY)
            )
        }
    }

    @Test
    fun `a month above twelve is never an expiry`() {
        for (text in listOf("Exp 13/26", "Exp 00/26", "Exp 99/26")) {
            assertEquals(emptyMap<SensitiveType, Int>(), counts(text))
        }
    }

    @Test
    fun `a dot is not an expiry separator`() {
        // Section and version numbers are the largest dotted family in the
        // corpus, and admitting `.` would hand every one of them a match.
        for (text in listOf("Exp 12.26", "Section 12.4.5.6", "Version 2.10.4.2")) {
            assertFalse(
                "$text was read as an expiry",
                counts(text).containsKey(SensitiveType.CARD_EXPIRY)
            )
        }
    }

    @Test
    fun `a decimal behind the year declines the match`() {
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Exp 12/26.5"))
        // ...but a full stop that ends a sentence does not.
        assertEquals("Expires **/**.", redact("Expires 12/26."))
    }

    // ── CVV ───────────────────────────────────────────────────────────────────

    @Test
    fun `every CVV label anchors its digits`() {
        for (label in listOf(
            "CVV", "cvv", "CVV2", "CVC", "cvc2", "CV2", "CSC",
            "Security code", "Card verification value", "Card verification code",
            "Kod keselamatan"
        )) {
            for (separator in listOf(" ", ": ", "=", " - ", "  ")) {
                val text = "$label$separator" + "123"
                assertEquals(
                    "\"$text\" was not read as a CVV",
                    mapOf(SensitiveType.CVV to 1),
                    counts(text)
                )
            }
        }
    }

    @Test
    fun `a CVV keeps its label and loses its digits`() {
        assertEquals("CVV: ***", redact("CVV: 123"))
        assertEquals("CVV: ****", redact("CVV: 1234"))
        assertEquals("Kod keselamatan **** diperlukan", redact("Kod keselamatan 4321 diperlukan"))
    }

    @Test
    fun `a bare three-digit number is never a CVV`() {
        for (text in listOf("123", "Room 123", "qty 999", "Total 250", "Page 123 of 240")) {
            assertFalse(
                "$text was read as a CVV",
                counts(text).containsKey(SensitiveType.CVV)
            )
        }
    }

    @Test
    fun `a label with no digits behind it claims nothing`() {
        assertEquals(emptyMap<SensitiveType, Int>(), counts("The CVV field was left blank"))
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Ask for the security code"))
    }

    @Test
    fun `a longer run behind a CVV label is left for the card detector`() {
        // Somebody pasted the wrong field. It is a card number whatever the
        // label says, and it must be masked as one rather than missed.
        assertEquals(mapOf(SensitiveType.CARD to 1), counts("cvv 4111111111111111"))
        assertEquals("cvv ************1111", redact("cvv 4111111111111111"))
    }

    @Test
    fun `cid is deliberately not a CVV label`() {
        // It is "correlation id" and "customer id" in every log this app is
        // pointed at. Pinned so re-adding it has to be a decision.
        assertEquals(emptyMap<SensitiveType, Int>(), counts("cid: 4821"))
    }

    // ── Both types, at every position of the control the user can reach ───────

    @Test
    fun `neither type reveals a digit, whatever the slider says`() {
        for (sample in listOf(
            "Exp 12/26",
            "Expiry date: 09/2027",
            "CVV: 123",
            "Security code 4321",
            "Card 4111111111111111 Exp 12/26 CVV 123"
        )) {
            for (glyph in MASK_GLYPHS) {
                for ((leading, trailing) in ALL_KEEP_COUNTS) {
                    val output = Redactor
                        .redactAllInText(sample, MaskPolicy(glyph, leading, trailing))
                        .output
                    for (secret in listOf("12/26", "09/2027", "123", "4321")) {
                        if (!sample.contains(secret)) continue
                        assertFalse(
                            "keep=$leading/$trailing left $secret in $output",
                            output.contains(secret)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the new types never spend the monotonicity budget`() {
        // Neither reveals anything, so against the card-only oracle the
        // comparison is exact at every slider position — no floor, no budget.
        for (sample in listOf(
            "Card 4111111111111111 Exp 12/26 CVV 123",
            "4111 1111 1111 1111 12/26",
            "Kad 4111111111111111\nLuput: 09/2027\nCVV 4321"
        )) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val before = digitCount(maskAllInText(sample, '*', leading, trailing))
                val after = digitCount(Redactor.redactAllInText(sample, '*', leading, trailing).output)
                assertTrue(
                    "keep=$leading/$trailing on \"$sample\" kept $after digits, was $before",
                    after <= before
                )
            }
        }
    }
}
