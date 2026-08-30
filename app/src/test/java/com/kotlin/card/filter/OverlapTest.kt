package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The five collisions the priority order exists to resolve. Each asserts the
 * *winning type by name*, not just the output, because a change that quietly
 * reorders the registry would still produce masked-looking text while leaking
 * the part that matters.
 */
class OverlapTest {

    private fun counts(text: String) = Redactor.redactAllInText(text, MaskPolicy('*')).counts

    @Test
    fun `an IC beats the card pattern that used to eat it`() {
        // The shipped leak: twelve digits with single dashes is a card run, so
        // 6+4 rendered this as 901231-**-5678 — full date of birth in the clear.
        assertEquals(mapOf(SensitiveType.MY_NRIC to 1), counts("901231-14-5678"))
        assertEquals(
            "901231-**-5678",
            maskAllInText("901231-14-5678", '*', 6, 4)
        )
        assertEquals(
            "******-**-****",
            Redactor.redactAllInText("901231-14-5678", '*', 6, 4).output
        )
    }

    @Test
    fun `an IBAN beats the card-length digit run inside it`() {
        assertEquals(
            mapOf(SensitiveType.IBAN to 1),
            counts("MT84MALT011000012345MTLCAST001S")
        )
    }

    @Test
    fun `a phone beats the card run inside it`() {
        assertEquals(mapOf(SensitiveType.PHONE to 1), counts("+8613800138000"))
    }

    @Test
    fun `a URL swallows the card and the IP in its own query string`() {
        assertEquals(
            mapOf(SensitiveType.URL to 1),
            counts("https://api.example.com/v1/4111111111111111?ip=203.0.113.45")
        )
        assertEquals(
            "https://api.example.com/****/****?ip=****",
            Redactor.redactAllInText(
                "https://api.example.com/v1/4111111111111111?ip=203.0.113.45",
                MaskPolicy('*')
            ).output
        )
    }

    @Test
    fun `a secret swallows the email, IP and card shapes inside a JWT`() {
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiIxMjM0NTY3ODkwIn0" +
            ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(mapOf(SensitiveType.SECRET to 1), counts(jwt))
    }

    @Test
    fun `summarizeCounts renders through the labeller it is given`() {
        // The UI passes one backed by <plurals> resources so the summary line is
        // translated. What must survive that is the ordering and the separator,
        // which live here rather than in the caller.
        val counts = mapOf(
            SensitiveType.CARD to 2,
            SensitiveType.MY_NRIC to 1,
            SensitiveType.CVV to 1
        )
        assertEquals("1 IC · 1 CVV · 2 cards", summarizeCounts(counts))
        assertEquals(
            "1:MY_NRIC · 1:CVV · 2:CARD",
            summarizeCounts(counts) { type, count -> "$count:$type" }
        )
        assertEquals("", summarizeCounts(emptyMap()) { _, _ -> "never" })
    }

    @Test
    fun `the registry is ordered by priority`() {
        assertEquals(Redactor.types.sortedBy { it.priority }, Redactor.types)
        assertEquals(SensitiveType.entries.size, Redactor.types.size)
    }

    @Test
    fun `a claimed span is never partially re-claimed, and the rest is not dropped`() {
        // This test used to read "IC 901231-14-5678 and card 4111111111111111",
        // and it passed against an engine that leaked. The words "and card" break
        // the card run in two, so that phrasing exercises the one arrangement in
        // which the collision cannot happen — it asserted the absence of the bug
        // by avoiding it. One space between the two values is the case that
        // matters, because that is a single card run under the shipped pattern.
        val text = "Ali 901231-14-5678 4111111111111111"
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.CARD to 1),
            counts(text)
        )
        assertEquals(
            "Ali ******-**-**** ************1111",
            Redactor.redactAllInText(text, MaskPolicy('*')).output
        )
    }

    @Test
    fun `a card one dash away from an IC is still a card`() {
        val text = "901231-14-5678-4111111111111111"
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.CARD to 1),
            counts(text)
        )
        assertEquals(
            "******-**-****-************1111",
            Redactor.redactAllInText(text, MaskPolicy('*')).output
        )
    }

    @Test
    fun `a phone or an IBAN beside a card does not swallow it either`() {
        assertEquals(
            mapOf(SensitiveType.PHONE to 1, SensitiveType.CARD to 1),
            counts("012-3456789 4111111111111111")
        )
        assertEquals(
            mapOf(SensitiveType.IBAN to 1, SensitiveType.CARD to 1),
            counts("GB29NWBK60161331926819 4111111111111111")
        )
        // …and in the other order, where the card is claimed first and the
        // higher-priority value is the one left over.
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.CARD to 1),
            counts("4111111111111111 901231-14-5678")
        )
    }

    @Test
    fun `a span another type keeps verbatim still obeys the card floor`() {
        // A URL keeps its authority and an email keeps its domain, so a card-length
        // digit run parked there would come back readable while the shipped masker
        // hides it. The floor in Redactor covers every type at once.
        assertEquals(
            "https://****************.example.com/****",
            Redactor.redactAllInText("https://4111111111111111.example.com/x", MaskPolicy('*')).output
        )
        assertEquals(
            "*@****************.com",
            Redactor.redactAllInText("a@4111111111111111.com", MaskPolicy('*')).output
        )
        assertEquals(
            "****************@x.com",
            Redactor.redactAllInText("4111111111111111@x.com", MaskPolicy('*')).output
        )
    }

    @Test
    fun `the floor is reported, not just applied`() {
        // These said "1 link" and "1 email". The masking was right and the count
        // was a lie by omission: the reason the output looks like that is that a
        // PAN was hidden inside it, and a user reading "1 link found" has been
        // told the opposite of the thing that matters most.
        assertEquals(
            mapOf(SensitiveType.URL to 1, SensitiveType.CARD to 1),
            counts("https://4111111111111111.example.com/x")
        )
        assertEquals(
            mapOf(SensitiveType.EMAIL to 1, SensitiveType.CARD to 1),
            counts("a@4111111111111111.com")
        )
        assertEquals("1 link · 1 card", summarizeCounts(counts("https://4111111111111111.example.com/x")))
        // …and a link that hides nothing card-shaped still counts as one link.
        assertEquals(
            mapOf(SensitiveType.URL to 1),
            counts("https://api.example.com/v1/4111111111111111?ip=203.0.113.45")
        )
    }

    @Test
    fun `two cards one space apart are counted as two`() {
        // One run, therefore one claim — but two numbers, and "1 card found" over
        // a result holding two is the counter under-reporting the only thing it
        // exists to report. The CARD count is what the shipped masker would have
        // counted across the same span rather than a flat one-per-claim.
        assertEquals(mapOf(SensitiveType.CARD to 2), counts("4111111111111111 4111111111111111"))
        assertEquals(mapOf(SensitiveType.CARD to 2), counts("4111111111111111 378282246310005"))
        assertEquals("2 cards", summarizeCounts(counts("4111111111111111 4111111111111111")))
        // A single card written in groups is still one card, not four.
        assertEquals(mapOf(SensitiveType.CARD to 1), counts("4111 1111 1111 1111"))
    }

    @Test
    fun `masking is never silent, and a count never means nothing was masked`() {
        // The counts are not decoration: `RedactDecision` gates the whole
        // PROCESS_TEXT sheet on `counts.isNotEmpty()`, so a mask with no count is
        // a mask the user is never shown, and a count with no mask offers to
        // "Replace selection" with text that is identical to what it replaces.
        for (separator in ALL_SEPARATORS) {
            for (text in adjacencyPairs(separator) + SENSITIVE_SAMPLES.map { withSeparator(it, separator) }) {
                for ((leading, trailing) in listOf(0 to 0, 0 to 4, 6 to 4, 0 to MAX_REVEALED_DIGITS)) {
                    val result = Redactor.redactAllInText(text, MaskPolicy('*', leading, trailing))
                    if (result.output != text) {
                        assertEquals(
                            "keep=$leading/$trailing masked \"$text\" into \"${result.output}\" " +
                                "and reported nothing",
                            true,
                            result.counts.isNotEmpty()
                        )
                    } else {
                        assertEquals(
                            "keep=$leading/$trailing reported ${result.counts} for \"$text\" " +
                                "but changed nothing",
                            emptyMap<SensitiveType, Int>(),
                            result.counts
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `an IBAN is still found when a grouped number follows it`() {
        // IBAN_REGEX's `(?:[ ]?[A-Z0-9]){11,30}` walks straight across the single
        // spaces of the card behind it; mod-97 then fails on the over-long string
        // and `find` never retried a shorter one, so the IBAN was reported as
        // absent and its bank and branch codes stayed on screen.
        assertEquals(
            mapOf(SensitiveType.IBAN to 1, SensitiveType.CARD to 1),
            counts("GB29NWBK60161331926819 4111 1111 1111 1111")
        )
        assertEquals(
            "GB****************6819 **** **** **** 1111",
            Redactor.redactAllInText("GB29NWBK60161331926819 4111 1111 1111 1111", MaskPolicy('*')).output
        )
        // The printed grouping is the form an IBAN actually arrives in, and it
        // has the same shape of neighbour problem.
        assertEquals(
            mapOf(SensitiveType.IBAN to 1, SensitiveType.CARD to 1),
            counts("GB29 NWBK 6016 1331 9268 19 4111 1111 1111 1111")
        )
    }
}
