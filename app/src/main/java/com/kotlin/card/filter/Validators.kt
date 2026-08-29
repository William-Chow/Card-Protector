package com.kotlin.card.filter

/**
 * Place-of-birth codes that appear in a real MyKad number: 01–16 (the states),
 * 21–59 and 60–85 (historic and foreign-birth allocations), plus 98 and 99.
 * Everything else is unassigned, which is most of the range — and that is the
 * only leverage available, since a MyKad number carries no checksum.
 */
private val NRIC_BIRTHPLACE: Set<Int> = ((1..16) + (21..85) + listOf(98, 99)).toSet()

/**
 * Structural check on the twelve digits of a MyKad number: YYMMDD, then a
 * place-of-birth code, then a four-digit serial.
 *
 * There is no checksum to lean on, so this plus the non-alphanumeric boundary
 * the detector's pattern insists on is the whole of the evidence. It is enough
 * to keep git SHAs, SKUs and 12-digit invoice numbers out.
 */
fun nricValid(digits: String): Boolean {
    if (digits.length != 12 || digits.any { it !in '0'..'9' }) return false
    val month = digits.substring(2, 4).toInt()
    val day = digits.substring(4, 6).toInt()
    val birthplace = digits.substring(6, 8).toInt()
    return month in 1..12 && day in 1..31 && birthplace in NRIC_BIRTHPLACE
}

/**
 * The ISO 13616 mod-97 check on a space-stripped IBAN: rotate the first four
 * characters to the end, map A–Z to 10–35, and read the result as one long
 * decimal that must leave a remainder of 1.
 *
 * Folded digit by digit rather than assembled into a [java.math.BigInteger] —
 * the running remainder never exceeds 96 * 100 + 35, so an Int carries it, and
 * the app takes no new dependency for this.
 */
fun ibanMod97(compact: String): Boolean {
    if (compact.length !in 15..34) return false
    if (compact[0] !in 'A'..'Z' || compact[1] !in 'A'..'Z') return false
    if (compact[2] !in '0'..'9' || compact[3] !in '0'..'9') return false
    var remainder = 0
    for (offset in compact.indices) {
        val ch = compact[(offset + 4) % compact.length]
        val value = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'A'..'Z' -> ch - 'A' + 10
            else -> return false
        }
        remainder = if (value >= 10) (remainder * 100 + value) % 97 else (remainder * 10 + value) % 97
    }
    return remainder == 1
}

/**
 * Whether a dotted quad is worth masking: a syntactically valid IPv4 address
 * that is neither an obvious false positive nor a private/reserved address.
 *
 * Both rejections are load-bearing. Without the all-single-digit rule, `1.2.3.4`
 * — a numbered list, a score line, a truncated version — is masked. Without the
 * private/reserved skip, `192.168.1.10` and `127.0.0.1` are masked, which makes
 * a support log unreadable while hiding nothing about anybody.
 */
fun ipv4Public(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    val octets = IntArray(4)
    for (index in 0..3) {
        val octet = parts[index].toIntOrNull() ?: return false
        if (octet !in 0..255) return false
        octets[index] = octet
    }
    if (parts.all { it.length == 1 }) return false
    val first = octets[0]
    val second = octets[1]
    return when {
        first == 0 || first == 127 || first >= 224 -> false   // this-network, loopback, multicast+
        first == 10 -> false                                  // 10/8
        first == 172 && second in 16..31 -> false             // 172.16/12
        first == 192 && second == 168 -> false                // 192.168/16
        first == 169 && second == 254 -> false                // 169.254/16 link-local
        else -> true
    }
}

/**
 * Words that introduce a *numbered thing* — a version, a build, a clause — in
 * English or Malay, and can introduce nothing else. A dotted quad behind one of
 * these is an enumeration, not an address, whatever its octets happen to be.
 *
 * Nothing here plausibly introduces a real address in the text this app sees;
 * the words that do (`ip`, `host`, `server`, `gateway`, `dns`) are deliberately
 * absent, and adding one would start dropping real addresses.
 */
private val ENUMERATOR_WORDS: Set<String> = setOf(
    "v", "ver", "version", "versions", "versi",
    "build", "builds", "rev", "revision",
    "section", "sec", "seksyen", "bahagian", "chapter", "bab",
    "clause", "fasal", "article", "perkara", "paragraph", "para",
    "langkah", "figure", "fig", "rajah", "jadual",
    "appendix", "lampiran", "schedule", "exhibit"
)

/**
 * Words that *usually* introduce a numbered thing but are ordinary English in
 * their own right, so they only suppress a quad that also looks like a version.
 *
 * All of these used to sit in [ENUMERATOR_WORDS] unconditionally, and that was a
 * false positive pointing the wrong way — the wrong way being the direction that
 * leaves real data on screen. "Release 203.0.113.45 is now live", "Item
 * 203.0.113.45 in the allowlist", "Rule 203.0.113.45", "Level 203.0.113.45" are
 * exactly the support-ticket and log sentences this app is pointed at, and every
 * one of them left a genuine public address in the clear. A word that can
 * introduce an address must not be allowed to veto masking on its own.
 */
private val WEAK_ENUMERATOR_WORDS: Set<String> = setOf(
    "release", "releases", "update", "updates", "patch", "patches",
    "rule", "rules", "table", "item", "items", "no", "nos",
    "level", "part", "step", "phase"
)

/**
 * Whether a dotted quad reads as a version number rather than an address: both
 * leading components below one hundred.
 *
 * Not a proof, and not meant to be — it is the second half of a two-part test
 * whose first half is a word like `patch` or `table` sitting right in front. A
 * real address can look like this (`45.33.32.156` does), so a weak word plus a
 * low-numbered quad is still suppressed and that residual is deliberate: what
 * matters is that no weak word can suppress `203.0.113.45`, which is the shape
 * an address in a support ticket actually has.
 */
private fun ipv4VersionShaped(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    val first = parts[0].toIntOrNull() ?: return false
    val second = parts[1].toIntOrNull() ?: return false
    return first < 100 && second < 100
}

/**
 * Whether the dotted quad at [start] in [text] is introduced by an enumerator
 * word, and so is a version or section number rather than an address.
 *
 * This is the third IPv4 guard, and it exists because the other two only *look*
 * like they cover version strings. The lookbehind kills `v1.10.4.2` and
 * [ipv4Public]'s all-single-digit rule kills `Section 3.4.1.2`, so the corpus —
 * which held exactly those two shapes — reported the class as handled. It is
 * not: `Build 1.10.4.2` and `Section 12.4.5.6` clear both guards and were being
 * masked, mid-sentence, in ordinary prose.
 *
 * Only the immediately preceding word counts, optionally through a `:`, `#` or
 * `-`. A full stop is deliberately *not* crossed, so a sentence that happens to
 * end in "…section." does not swallow the address that starts the next one.
 *
 * [quad] is the dotted quad itself, needed because half the vocabulary — see
 * [WEAK_ENUMERATOR_WORDS] — only counts when the number also reads as a version.
 */
fun ipv4Enumerated(text: String, start: Int, quad: String): Boolean {
    var index = start - 1
    var separated = false
    while (index >= 0 && (text[index] == ' ' || text[index] == '\t')) {
        index--
        separated = true
    }
    if (index >= 0 && (text[index] == ':' || text[index] == '#' || text[index] == '-')) {
        index--
        separated = true
        while (index >= 0 && (text[index] == ' ' || text[index] == '\t')) index--
    }
    if (!separated) return false
    val wordEnd = index
    while (index >= 0 && text[index].isLetter()) index--
    if (index == wordEnd) return false
    val word = text.substring(index + 1, wordEnd + 1).lowercase()
    if (word in ENUMERATOR_WORDS) return true
    return word in WEAK_ENUMERATOR_WORDS && ipv4VersionShaped(quad)
}

/**
 * Whether a Malaysian number's national significant digits (the leading `+60`
 * or trunk `0` already removed) belong to a person rather than a billboard.
 *
 * 1-300 and 1-800 lines are published customer-service numbers; masking them is
 * noise, not privacy.
 */
fun phoneDigitsPlausible(national: String): Boolean =
    !national.startsWith("1300") && !national.startsWith("1800")
