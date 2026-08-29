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

    @Test
    fun `a numbered version or section is not an address`() {
        // The corpus only ever held "v1.10.4.2" and "Section 3.4.1.2", which the
        // lookbehind and the all-single-digit rule kill respectively — so between
        // them they made this class look covered. Drop the "v", give an octet two
        // digits, and it fired mid-sentence.
        assertEquals("Build 1.10.4.2 on Chrome/120.0.6099.234", redact("Build 1.10.4.2 on Chrome/120.0.6099.234"))
        assertEquals("Section 12.4.5.6 of the agreement", redact("Section 12.4.5.6 of the agreement"))
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Patch 4.10.200.3 is mandatory"))
        // An address that is *not* introduced as a numbered thing still goes.
        assertEquals("Blocked 203.***.***.*** at the edge", redact("Blocked 203.0.113.45 at the edge"))
    }

    @Test
    fun `an ordinary word in front of an address does not spare it`() {
        // The enumerator list was written to spare "Build 1.10.4.2", and it grew
        // to forty-three words. Eleven of them — release, update, patch, rule,
        // table, item, no, level, part, step, phase — are ordinary English in
        // exactly the support tickets and server logs this app is pointed at, and
        // each one was silently vetoing the masking of a real public address.
        // A false positive that leaves data on screen is not a false positive in
        // the harmless direction.
        for (word in listOf("Release", "Update", "Patch", "Rule", "Table", "Item", "No", "Level", "Part", "Step", "Phase")) {
            assertEquals(
                "$word 203.0.113.45 was masked as: ${redact("$word 203.0.113.45 is now live")}",
                mapOf(SensitiveType.IPV4 to 1),
                counts("$word 203.0.113.45 is now live")
            )
        }
        assertEquals("Release 203.***.***.*** is now live", redact("Release 203.0.113.45 is now live"))
        assertEquals("Item 203.***.***.*** in the allowlist", redact("Item 203.0.113.45 in the allowlist"))
        // They keep their original job, because a version-shaped quad behind one
        // of them still reads as an enumeration.
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Table 12.4.5.6 lists the fees"))
        assertEquals(emptyMap<SensitiveType, Int>(), counts("Patch 4.10.200.3 is mandatory"))
    }

    @Test
    fun `a card-length run is masked as one number, not as a matched part and a tail`() {
        // The family two rounds of review found. `123456789012` and the PAN are
        // one twenty-eight digit run once the no-break space folds, the card
        // pattern's nineteen-digit ceiling stopped the match inside the PAN's
        // third group, and the eight digits past it were below the twelve-digit
        // floor so nothing else ever claimed them. At the slider's top the whole
        // PAN came back verbatim, with the counts reading "1 card found".
        assertEquals(
            "Akaun ************ **** **** **** 1111",
            redact("Akaun 123456789012 4111 1111 1111 1111")
        )
        assertEquals(
            mapOf(SensitiveType.CARD to 1),
            counts("Akaun 123456789012 4111 1111 1111 1111")
        )
        // At the slider's ceiling, ten digits of the run — not sixteen of the PAN.
        assertEquals(
            "Akaun ************ **** **11 1111 1111",
            Redactor.redactAllInText(
                "Akaun 123456789012 4111 1111 1111 1111",
                MaskPolicy('*', 0, MAX_REVEALED_DIGITS)
            ).output
        )
    }

    @Test
    fun `a leftover fragment of a run is masked whole, not to its own last four`() {
        // Nothing offers a candidate for eleven digits: they are one short of the
        // card pattern's floor, so the resolver's re-offer loop has nothing to
        // re-offer and the run sweep is what covers them. They reveal nothing,
        // because the last four digits of a fragment are four digits from the
        // middle of somebody's number rather than the tail of a card.
        assertEquals("******-**-**** ***********", redact("901231-14-5678 41111111111"))
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.CARD to 1),
            counts("901231-14-5678 41111111111")
        )
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
    fun `an unlabelled four-part version number is still masked`() {
        // Pinned rather than fixed. Nothing structural separates this from a real
        // address in 1.0.0.0/8, and every guard that would catch it drops real
        // addresses too. It fails in the safe direction: unreadable, not unmasked.
        assertEquals("Upgraded to 1.***.***.*** yesterday", redact("Upgraded to 1.10.4.2 yesterday"))
    }

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
        //
        // The `2026` goes too, and used to survive. It is not a separate value:
        // `2026-000123456789` is one sixteen-digit run, the IC rule took twelve
        // digits out of the middle of it, and what is left is a fragment of a
        // number rather than a year — the run sweep in `Redactor` masks it whole.
        // The card-only masker hides it as well, so this is the *same* answer
        // reached for a better reason.
        assertEquals("PO-****-************", redact("PO-2026-000123456789"))
        assertEquals("PO-****-********6789", maskAllInText("PO-2026-000123456789", '*', 0, 4))
    }
}
