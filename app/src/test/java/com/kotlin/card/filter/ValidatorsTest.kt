package com.kotlin.card.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checks that let the patterns be loose without being noisy. Each one is
 * tested against real values *and* against near-misses, because a validator that
 * accepts everything is worse than none — it makes the pattern look safe.
 */
class ValidatorsTest {

    private val realIbans = listOf(
        "GB29NWBK60161331926819",
        "DE89370400440532013000",
        "FR1420041010050500013M02606",
        "MT84MALT011000012345MTLCAST001S",
        "NL91ABNA0417164300",
        "CH9300762011623852957"
    )

    @Test
    fun `mod-97 accepts real IBANs`() {
        for (iban in realIbans) assertTrue(iban, ibanMod97(iban))
    }

    @Test
    fun `mod-97 rejects every single-digit mutation of a real IBAN`() {
        for (iban in realIbans) {
            for (index in iban.indices) {
                val ch = iban[index]
                if (ch !in '0'..'9') continue
                val bumped = if (ch == '9') '0' else ch + 1
                val mutated = iban.substring(0, index) + bumped + iban.substring(index + 1)
                assertFalse("$mutated passed", ibanMod97(mutated))
            }
        }
    }

    @Test
    fun `mod-97 rejects references that merely look like IBANs`() {
        assertFalse(ibanMod97("MY24INV000123456789"))
        assertFalse(ibanMod97("EE123456789MY"))
        assertFalse(ibanMod97("AB123456789012"))
        assertFalse(ibanMod97("GB29"))
    }

    @Test
    fun `NRIC accepts real numbers and rejects impossible dates`() {
        assertTrue(nricValid("901231145678"))
        assertTrue(nricValid("010101010001"))
        // The value MaskingTest masks as a card: month 34 does not exist, so
        // this must not be reclassified and no shipped test moves.
        assertFalse(nricValid("123456787890"))
        assertFalse(nricValid("901331145678"))  // day 31 of month 13
        assertFalse(nricValid("901232145678"))  // day 32
        assertFalse(nricValid("901231175678"))  // birthplace 17 is unassigned
        assertFalse(nricValid("90123114567"))   // eleven digits
    }

    @Test
    fun `IPv4 accepts public addresses`() {
        assertTrue(ipv4Public("203.0.113.45"))
        assertTrue(ipv4Public("52.94.236.248"))
        assertTrue(ipv4Public("1.1.1.10"))
    }

    @Test
    fun `IPv4 rejects the two classes that would break the corpus`() {
        // Every octet a single digit: a version, a list, a score.
        assertFalse(ipv4Public("1.2.3.4"))
        assertFalse(ipv4Public("9.9.9.9"))
        // And the cost of that rule, named here so it is not misattributed: the
        // public resolvers are declined by *this* check and not by the
        // enumerator-word list, so trimming that list — which was done, because
        // it was suppressing real addresses — does not and cannot recover them.
        assertFalse(ipv4Public("8.8.8.8"))
        assertFalse(ipv4Public("1.1.1.1"))
        // Private and reserved: masking these hides nothing and ruins a log.
        assertFalse(ipv4Public("10.0.0.5"))
        assertFalse(ipv4Public("172.16.0.1"))
        assertFalse(ipv4Public("172.31.255.254"))
        assertTrue(ipv4Public("172.32.0.1"))
        assertFalse(ipv4Public("192.168.1.10"))
        assertFalse(ipv4Public("127.0.0.1"))
        assertFalse(ipv4Public("169.254.1.1"))
        assertFalse(ipv4Public("0.0.0.10"))
        assertFalse(ipv4Public("224.0.0.1"))
        assertFalse(ipv4Public("256.1.1.1"))
    }

    @Test
    fun `a quad introduced by an enumerator word is not an address`() {
        // The shapes the old corpus missed: neither the lookbehind nor the
        // all-single-digit rule touches these, and both were being masked.
        for (line in listOf(
            "Build 1.10.4.2",
            "Section 12.4.5.6 of the agreement",
            "Version 2.10.4.2 shipped",
            "Rujuk Seksyen 12.4.5.6 perjanjian",
            "Table 12.4.5.6 lists the fees",
            "Clause: 12.4.5.6",
            "patch #4.10.200.3"
        )) {
            val at = line.indexOfFirst { it.isDigit() }
            assertTrue(line, ipv4Enumerated(line, at, quadAt(line, at)))
        }
    }

    @Test
    fun `an address is still an address`() {
        // None of these words introduces an enumeration, and a full stop is not
        // crossed — so a sentence ending in "…section." cannot eat the next one.
        for (line in listOf(
            "Server 203.0.113.45",
            "ip 203.0.113.45",
            "Blocked 203.0.113.45 at the gateway",
            "See the section. 203.0.113.45 is the origin",
            "203.0.113.45",
            "Supersection 203.0.113.45"
        )) {
            val at = line.indexOfFirst { it.isDigit() }
            assertFalse(line, ipv4Enumerated(line, at, quadAt(line, at)))
        }
    }

    @Test
    fun `an ordinary word cannot veto masking on its own`() {
        // The eleven words that used to suppress unconditionally. Each one is
        // ordinary English in exactly the support tickets and logs this app is
        // pointed at, and each one was leaving a genuine public address on screen.
        for (word in listOf(
            "Release", "release", "Update", "Patch", "Rule", "Table",
            "Item", "No", "Level", "Part", "Step", "Phase"
        )) {
            val line = "$word 203.0.113.45 is the origin"
            val at = line.indexOfFirst { it.isDigit() }
            assertFalse(line, ipv4Enumerated(line, at, quadAt(line, at)))
        }
    }

    @Test
    fun `an ordinary word still suppresses a version-shaped quad`() {
        // …and the other half of the same rule: the words stay useful for what
        // they were added for, which was "Patch 4.10.200.3", not an address.
        for (line in listOf("Patch 4.10.200.3 is mandatory", "Table 12.4.5.6 lists the fees")) {
            val at = line.indexOfFirst { it.isDigit() }
            assertTrue(line, ipv4Enumerated(line, at, quadAt(line, at)))
        }
    }

    /** The dotted quad starting at [at] — digits and dots, nothing else. */
    private fun quadAt(line: String, at: Int): String =
        line.drop(at).takeWhile { it.isDigit() || it == '.' }.trimEnd('.')

    @Test
    fun `service lines are told apart from personal numbers`() {
        assertFalse(phoneDigitsPlausible("1300881234"))
        assertFalse(phoneDigitsPlausible("1800123456"))
        assertTrue(phoneDigitsPlausible("123456789"))
        assertTrue(phoneDigitsPlausible("312345678"))
    }
}
