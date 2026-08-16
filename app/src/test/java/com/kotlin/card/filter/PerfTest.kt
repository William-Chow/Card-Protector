package com.kotlin.card.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catastrophic backtracking is not a theoretical concern here: the input arrives
 * from arbitrary third-party apps through PROCESS_TEXT, and a hang inside the
 * text-selection toolbar looks like the *host* app freezing.
 *
 * The `timeout` is the point of these tests. Any future pattern shaped like
 * `(\d+[ -]?)+` will blow through it long before it reaches a user.
 */
class PerfTest {

    /** 64 KB — the cap the batch input field enforces. */
    private fun pathological(): String {
        val fragments = listOf(
            "1234567890123456789012345 ",
            "----------------------- ",
            "aaaaaaaaaaaaaaaaaaaa@bbbbbbbbbbbbbbbbbbbb ",
            "AB12CDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ",
            "https://a.b/cccccccccccccccccccccccccccccccc?d=e ",
            "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 ",
            "+60 +60 +60 +60 +60 +60 +60 +60 ",
            "999.999.999.999.999.999.999.999 ",
            "eyJ.eyJ.eyJ eyJ.eyJ.eyJ password: password: "
        )
        val sb = StringBuilder(70_000)
        var index = 0
        while (sb.length < 64 * 1024) {
            sb.append(fragments[index++ % fragments.size])
        }
        return sb.toString()
    }

    @Test(timeout = 10_000)
    fun `64 KB of pathological input redacts without pathological backtracking`() {
        val input = pathological()
        assertTrue(input.length >= 64 * 1024)
        val output = Redactor.redactAllInText(input, MaskPolicy('*')).output
        assertTrue(output.isNotEmpty())
    }

    @Test(timeout = 10_000)
    fun `a single unbroken digit run is linear, not quadratic`() {
        val digits = "1234567890".repeat(6_600)
        val result = Redactor.redactAllInText(digits, MaskPolicy('*'))
        assertEquals(digits.length, result.output.length)
        assertTrue(result.counts.getValue(SensitiveType.CARD) > 0)
    }

    @Test(timeout = 10_000)
    fun `normalization of a large input is linear`() {
        val text = "\uFF11\u200B-\u00A0a".repeat(20_000)
        assertEquals(text.length, normalizeForScan(text).length)
    }
}
