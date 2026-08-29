package com.kotlin.card.filter

/** One canonical sensitive sample per type, for the property-based tests. */
val TYPE_SAMPLES: Map<SensitiveType, String> = mapOf(
    SensitiveType.SECRET to "AKIAIOSFODNN7EXAMPLE",
    SensitiveType.URL to "https://drive.google.com/file/d/1a2B3c/view?usp=sharing",
    SensitiveType.EMAIL to "siti.aminah@sekolah.edu.my",
    SensitiveType.IBAN to "MT84MALT011000012345MTLCAST001S",
    SensitiveType.MY_NRIC to "901231-14-5678",
    SensitiveType.PHONE to "+60 12-345 6789",
    SensitiveType.CARD to "4111111111111111",
    SensitiveType.IPV4 to "203.0.113.45"
)

/**
 * Strings holding **more than one** sensitive value, most of them adjacent.
 *
 * Every single-value sample is a sample that could never produce the shape that
 * mattered: two values separated by a single space or a single dash — the two
 * separators the card pattern spans — form one card run, and a resolver that
 * drops an overlapping match whole then leaves the second value entirely
 * unmasked. A one-value corpus reports that engine as monotone. These are the
 * shapes that ask the real question.
 */
val MULTI_VALUE_SAMPLES: List<String> = listOf(
    "901231-14-5678 4111111111111111",
    "901231145678 4111111111111111",
    "4111111111111111 901231-14-5678",
    "901231-14-5678-4111111111111111",
    "012-3456789 4111111111111111",
    "4111111111111111 012-3456789",
    "+60123456789 4111111111111111",
    "GB29NWBK60161331926819 4111111111111111",
    "Ali 901231-14-5678 4111 1111 1111 1111",
    "901231-14-5678 012-3456789 4111111111111111",
    "IC 901231-14-5678, card 4111111111111111, tel 012-3456789",
    "Nama: ALI\nIC: 901231-14-5678\nKad: 4111 1111 1111 1111",
    "a.b@c.com 4111111111111111",
    "AKIAIOSFODNN7EXAMPLE 4111111111111111",
    "203.0.113.45 4111111111111111",
    "4111111111111111 4222222222222",
    "https://x.io/p?id=4111111111111111 012-3456789",
    "TAC: 483920 4111111111111111"
)

/** A wider spread of real sensitive values, used where one per type is too thin. */
val SENSITIVE_SAMPLES: List<String> = TYPE_SAMPLES.values + listOf(
    "Tel: 03-1234 5678",
    "012-3456789",
    "04-123 4567",
    "+8613800138000",
    "IC 901231-14-5678",
    "901231145678",
    "GB29 NWBK 60161331926819",
    "a@x.com",
    "password: hunter2",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJzdWIiOiIxMjM0NTY3ODkwIn0" +
        ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
) + MULTI_VALUE_SAMPLES

/** How many characters of [text] are the mask glyph [glyph]. */
fun maskCount(text: String, glyph: Char): Int = text.count { it == glyph }

/** How many digits survived masking. */
fun digitCount(text: String): Int = text.count { it.isDigit() }

/**
 * The twenty-four characters [normalizeForScan] folds onto `' '` or `'-'`.
 *
 * These are the whole reason this file needed a second corpus. The engine scans
 * the *normalized* string, so every one of them joins two numbers into a single
 * digit run — while the shipped `CARD_REGEX`, which reads the raw text, sees two
 * separate numbers. Every sample in this file was ASCII-only, so the entire
 * folded family was invisible to the suite written to catch adjacency leaks.
 *
 * Kept in one list, derived from the same ranges [normalizeForScan] folds, so a
 * new fold cannot be added there without a line here.
 */
val FOLDED_SEPARATORS: List<Char> = buildList {
    add('\u00A0'); add('\u202F')                            // no-break, narrow no-break
    for (ch in '\u2000'..'\u200A') add(ch)                  // en/em/thin/figure and the rest
    add('\u200B'); add('\u200C'); add('\u200D'); add('\uFEFF') // zero-width, non-joiner, joiner, BOM
    for (ch in '\u2010'..'\u2015') add(ch)                  // hyphen through horizontal bar
    add('\u2212')                                           // minus sign
}

/** Every folded separator, plus the two ASCII characters they fold onto. */
val ALL_SEPARATORS: List<Char> = FOLDED_SEPARATORS + listOf(' ', '-')

/** [text] with every ASCII space and dash replaced by [separator]. */
fun withSeparator(text: String, separator: Char): String =
    buildString(text.length) {
        for (ch in text) append(if (ch == ' ' || ch == '-') separator else ch)
    }

/**
 * The operands of an adjacency: values that are sensitive on their own, plus the
 * bare numeric runs that only become dangerous next to something else.
 */
val ADJACENCY_OPERANDS: List<String> = listOf(
    "4111 1111 1111 1111",
    "4111111111111111",
    "4222222222222",
    "123456789012",
    "41111111111",
    "901231-14-5678",
    "012-3456789",
    "+60123456789",
    "GB29NWBK60161331926819",
    "MT84MALT011000012345MTLCAST001S",
    "203.0.113.45",
    "a.b@c.com",
    "AKIAIOSFODNN7EXAMPLE"
)

/**
 * Every ordered pair of [ADJACENCY_OPERANDS] joined by [separator] — both orders,
 * because which value the resolver claims first changes which one is left over.
 */
fun adjacencyPairs(separator: Char): Sequence<String> = sequence {
    for (left in ADJACENCY_OPERANDS.indices) {
        for (right in ADJACENCY_OPERANDS.indices) {
            if (left == right) continue
            yield(ADJACENCY_OPERANDS[left] + separator + ADJACENCY_OPERANDS[right])
        }
    }
}
