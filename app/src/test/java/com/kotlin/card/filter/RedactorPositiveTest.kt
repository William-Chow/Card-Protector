package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The other half of the bargain: things that must be caught, with their exact
 * masked form pinned so a policy change has to be deliberate.
 *
 * Every case uses the shipped default policy — `*`, keep last 4 — so the CARD
 * column reads the way it does in the app today.
 */
class RedactorPositiveTest {

    private fun redact(text: String) = Redactor.redactAllInText(text, MaskPolicy('*')).output

    private fun counts(text: String) = Redactor.redactAllInText(text, MaskPolicy('*')).counts

    // ── Malaysian phone numbers ───────────────────────────────────────────────

    @Test
    fun `malaysian landlines and mobiles are masked to their last three digits`() {
        assertEquals("Tel: **-**** *678", redact("Tel: 03-1234 5678"))
        assertEquals("+60 **-*** *789", redact("+60 12-345 6789"))
        assertEquals("***-****789", redact("012-3456789"))
        assertEquals("**-*** *567", redact("04-123 4567"))
    }

    @Test
    fun `an international number keeps its plus and nothing else`() {
        assertEquals("+**********000", redact("+8613800138000"))
        assertEquals(mapOf(SensitiveType.PHONE to 1), counts("+8613800138000"))
    }

    @Test
    fun `published service lines are not claimed as phone numbers`() {
        // It still gets masked — twelve digits separated by a space is a card
        // run and has been since release — but not as somebody's phone number,
        // which is what the 1-300 / 1-800 post-filter exists to prevent.
        assertEquals(
            mapOf(SensitiveType.CARD to 1),
            counts("+60 1300881234")
        )
    }

    // ── Malaysian IC ──────────────────────────────────────────────────────────

    @Test
    fun `an IC reveals nothing at all`() {
        assertEquals("IC ******-**-****", redact("IC 901231-14-5678"))
        assertEquals("************", redact("901231145678"))
    }

    // ── Email, URL, IBAN, IP ──────────────────────────────────────────────────

    @Test
    fun `an email keeps one initial and the whole domain`() {
        assertEquals("s**********@sekolah.edu.my", redact("siti.aminah@sekolah.edu.my"))
    }

    @Test
    fun `a single-character local part reveals nothing`() {
        assertEquals("*@x.com", redact("a@x.com"))
    }

    @Test
    fun `a URL keeps its host and query keys and loses the rest`() {
        assertEquals(
            "https://drive.google.com/****/****/****/****?usp=****",
            redact("https://drive.google.com/file/d/1a2B3c/view?usp=sharing")
        )
    }

    @Test
    fun `a bare link with nothing to hide is released untouched`() {
        assertEquals("Visit https://example.com today", redact("Visit https://example.com today"))
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Visit https://example.com today"))
    }

    @Test
    fun `a trailing full stop is not part of the link`() {
        assertEquals("See https://a.io/****.", redact("See https://a.io/secret."))
    }

    @Test
    fun `credentials in a URL authority are masked even with no path`() {
        assertEquals("https://****@host.com", redact("https://admin:pw@host.com"))
    }

    @Test
    fun `IBANs keep the country code and last four, spaced or not`() {
        assertEquals("GB** **** **********6819", redact("GB29 NWBK 60161331926819"))
        assertEquals(
            "MT*************************001S",
            redact("MT84MALT011000012345MTLCAST001S")
        )
    }

    @Test
    fun `a public IP is masked below its first octet`() {
        assertEquals("203.***.***.***", redact("203.0.113.45"))
    }

    // ── Secrets ───────────────────────────────────────────────────────────────

    @Test
    fun `tokens collapse to their brand marker`() {
        assertEquals("AKIA********", redact("AKIAIOSFODNN7EXAMPLE"))
        assertEquals("password: ********", redact("password: hunter2"))
        assertEquals(
            "eyJ********",
            redact(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJzdWIiOiIxMjM0NTY3ODkwIn0" +
                    ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            )
        )
    }

    @Test
    fun `a malaysian TAC is treated as a secret`() {
        assertEquals("TAC: ********", redact("TAC: 483920"))
    }

    // ── Card, unchanged, plus the overspill it used to leak ───────────────────

    @Test
    fun `card masking is untouched`() {
        assertEquals("pay ************1111 now", redact("pay 4111111111111111 now"))
    }

    @Test
    fun `a run longer than the pattern's ceiling no longer leaks its tail`() {
        // The shipped pattern matches at most 19 digits, so the last three of a
        // 22-digit run survive it. Span expansion is what closes that.
        assertEquals("******************1234", redact("Ref 1234567890123456781234").drop(4))
    }

    // ── Counting ──────────────────────────────────────────────────────────────

    @Test
    fun `the summary line names what was found`() {
        val text = "Call 012-3456789 or mail a.b@c.com about 4111111111111111 and 4222222222222"
        assertEquals("1 email · 1 phone · 2 cards", summarizeCounts(counts(text)))
    }

    // ── Known residuals, pinned so they cannot drift unnoticed ────────────────

    @Test
    fun `a reference number shaped exactly like an IC is over-masked, not under-masked`() {
        // Found by an adversarial sweep, kept deliberately.
        //
        // `000123456789` parses as a structurally valid MyKad: YY=00, MM=01,
        // DD=23, birthplace=45, serial 6789. A MyKad number carries no checksum,
        // so nothing distinguishes it from this purchase-order tail without
        // context the redactor does not have.
        //
        // The failure is in the safe direction and that is why it stays: the
        // card-only engine revealed the last four digits here, and the IC rule
        // reveals none. Tightening NRIC to exclude it would mean loosening the
        // structural test, which trades a harmless over-mask for the risk of
        // leaving a real IC readable. Not a trade worth making in a redactor.
        assertEquals("PO-2026-************", redact("PO-2026-000123456789"))
        assertEquals("PO-****-********6789", maskAllInText("PO-2026-000123456789", '*', 0, 4))
    }
}
