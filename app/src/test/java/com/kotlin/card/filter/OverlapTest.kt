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
    fun `the registry is ordered by priority`() {
        assertEquals(Redactor.types.sortedBy { it.priority }, Redactor.types)
        assertEquals(SensitiveType.entries.size, Redactor.types.size)
    }

    @Test
    fun `a claimed span is never partially re-claimed`() {
        val text = "IC 901231-14-5678 and card 4111111111111111"
        assertEquals(
            mapOf(SensitiveType.MY_NRIC to 1, SensitiveType.CARD to 1),
            counts(text)
        )
    }
}
