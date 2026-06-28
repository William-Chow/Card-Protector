package com.kotlin.card.filter

/**
 * Pure-Kotlin helpers around a card number — no assets, no network.
 * Both are strictly cosmetic: they label/reassure but never gate masking.
 */
object CardTools {

    /** Detect the card brand from the BIN prefix. Falls back to a generic "CARD". */
    fun detectBrand(input: String): String {
        val d = input.filter { it.isDigit() }
        if (d.isEmpty()) return "CARD"
        val p2 = d.take(2).toIntOrNull() ?: -1
        val p4 = d.take(4).toIntOrNull() ?: -1
        return when {
            d.startsWith("4") -> "VISA"
            p2 in 51..55 -> "MASTERCARD"
            d.length >= 4 && p4 in 2221..2720 -> "MASTERCARD"
            d.startsWith("34") || d.startsWith("37") -> "AMEX"
            d.startsWith("6011") || d.startsWith("65") -> "DISCOVER"
            else -> "CARD"
        }
    }

    /** Luhn checksum — cosmetic "looks valid" reassurance only. */
    fun isLuhnValid(input: String): Boolean {
        val d = input.filter { it.isDigit() }
        if (d.length < 12) return false
        var sum = 0
        var alternate = false
        for (i in d.indices.reversed()) {
            var n = d[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
