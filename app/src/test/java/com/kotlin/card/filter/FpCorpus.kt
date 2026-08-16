package com.kotlin.card.filter

/** The false-positive corpus, one string per line, comments and blanks stripped. */
object FpCorpus {

    val lines: List<String> by lazy {
        val stream = requireNotNull(FpCorpus::class.java.getResourceAsStream("/fp_corpus.txt")) {
            "fp_corpus.txt is missing from the unit-test resources"
        }
        stream.bufferedReader().useLines { sequence ->
            sequence.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    }

    /** The whole corpus as one document, to catch interactions across lines. */
    val joined: String by lazy { lines.joinToString("\n") }
}

/** Every mask glyph the picker in MainActivity offers. */
val MASK_GLYPHS = listOf('*', '•', '#', 'x', '$', '!', '@', '%', '^', '&')

/** Every (mode, keepN) the Reveal control can produce. */
val ALL_KEEP_COUNTS: List<Pair<Int, Int>> =
    MaskMode.entries.flatMap { mode -> (0..MAX_REVEALED_DIGITS).map { keepCounts(mode, it) } }
