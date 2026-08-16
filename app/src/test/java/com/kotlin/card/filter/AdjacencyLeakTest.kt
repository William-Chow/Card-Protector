package com.kotlin.card.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drop-on-overlap gate.
 *
 * A card number written one space or one dash away from an IC, a phone number or
 * an IBAN forms a *single* run under the shipped `CARD_REGEX`, because that
 * pattern spans exactly those two separators. The higher-priority detector claims
 * its half first, the card match then overlaps that claim, and a resolver that
 * drops an overlapping candidate whole leaves the entire PAN in the clear — a
 * regression against the shipped card-only masker, which masks it.
 *
 * Worse than silent: with the card match dropped, `counts` carries no CARD entry,
 * so the batch counter and the PROCESS_TEXT sheet both report "1 IC found" while
 * a full sixteen-digit PAN sits in the visible result, and "Replace selection"
 * would write it back into the host app.
 *
 * So the assertion here is the invariant itself, at every mask mode and every
 * slider position: **never fewer masked digits than the shipped card-only
 * behaviour**, and the card is named in the counts so the UI cannot claim
 * otherwise.
 */
class AdjacencyLeakTest {

    /** A card adjacent to a higher-priority value, across the separators CARD spans. */
    private val collisions = listOf(
        "901231-14-5678 4111111111111111",
        "901231145678 4111111111111111",
        "4111111111111111 901231-14-5678",
        "901231-14-5678-4111111111111111",
        "012-3456789 4111111111111111",
        "+60123456789 4111111111111111",
        "GB29NWBK60161331926819 4111111111111111",
        "Ali 901231-14-5678 4111 1111 1111 1111"
    )

    /** The shipped behaviour this engine may never fall below. */
    private fun cardOnlyOracle(text: String, leading: Int, trailing: Int): String =
        maskAllInText(text, '*', leading, trailing)

    @Test
    fun `an adjacent claim never lets the card through unmasked`() {
        for (text in collisions) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val result = Redactor.redactAllInText(text, MaskPolicy('*', leading, trailing))
                val oracle = cardOnlyOracle(text, leading, trailing)
                assertTrue(
                    "keep=$leading/$trailing on \"$text\"\n" +
                        "  card-only oracle: ${oracle.replace("\n", "\\n")}\n" +
                        "  this engine:      ${result.output.replace("\n", "\\n")}",
                    digitCount(result.output) <= digitCount(oracle)
                )
                assertTrue(
                    "keep=$leading/$trailing on \"$text\" reported ${result.counts} — " +
                        "no CARD, so the UI would tell the user no card was found",
                    result.counts.containsKey(SensitiveType.CARD)
                )
            }
        }
    }

    @Test
    fun `the PAN itself never survives, whatever the slider says`() {
        for (text in collisions) {
            for ((leading, trailing) in ALL_KEEP_COUNTS) {
                val output = Redactor.redactAllInText(text, MaskPolicy('*', leading, trailing)).output
                assertFalse(
                    "keep=$leading/$trailing on \"$text\" left the PAN in: $output",
                    output.filter { it.isDigit() }.contains("4111111111111111")
                )
            }
        }
    }

    @Test
    fun `the separators that are safe stay safe`() {
        // Comma, tab, newline and a double space all break the CARD run, so these
        // never took the drop-on-overlap path. Pinned so a future resolver change
        // cannot regress the easy cases while fixing the hard ones.
        for (separator in listOf(", ", "\t", "\n", "  ")) {
            val text = "901231-14-5678" + separator + "4111111111111111"
            val counts = Redactor.redactAllInText(text, MaskPolicy('*')).counts
            assertTrue(
                "separator=${separator.replace("\n", "\\n").replace("\t", "\\t")} gave $counts",
                counts.containsKey(SensitiveType.CARD) && counts.containsKey(SensitiveType.MY_NRIC)
            )
        }
    }

    @Test
    fun `the counts and the output agree with each other`() {
        val text = "Ali 901231-14-5678 4111111111111111"
        val result = Redactor.redactAllInText(text, MaskPolicy('*'))
        assertTrue(
            "summary said \"${summarizeCounts(result.counts)}\" for ${result.output}",
            summarizeCounts(result.counts) == "1 IC · 1 card"
        )
        assertTrue(result.output == "Ali ******-**-**** ************1111")
    }
}
