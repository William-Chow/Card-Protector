package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.ScanText
import com.kotlin.card.filter.SensitiveType

/**
 * A link, and only a link that says so.
 *
 * A scheme or a literal `www.` is **mandatory**. Bare-domain detection —
 * matching `example.com` on the strength of the TLD alone — is the single
 * largest available source of false positives in this app: it eats `Masking.kt`,
 * `report.pdf`, `e.g.`, `etc.` and every dotted version string. Requiring the
 * scheme is what buys the rest of the pattern its freedom to be greedy.
 *
 * The trailing character class excludes the brackets and quotes that wrap a URL
 * in prose and markdown; sentence punctuation is trimmed separately in code,
 * because a `.` or `,` is legal *inside* a URL and only suspicious at the end.
 */
private val URL_REGEX = Regex("""(?<![A-Za-z0-9@])(?:https?://|www\.)[^\s<>"'`)\]}]+""")

/** Punctuation that ends a sentence rather than a URL. */
private const val TRAILING_PUNCTUATION = ".,;:!?'\""

/** Path segments, query values and fragments all collapse to this many glyphs. */
private const val SEGMENT_WIDTH = 4

/**
 * Links.
 *
 * Outranks everything but SECRET, because a long identifier in a query string
 * and an IP-literal host are both things a lower-priority detector would
 * otherwise carve out of the middle of a URL.
 *
 * Accepted, documented gap: an IP-literal host inside a URL stays visible, since
 * the authority is kept whole.
 */
object UrlDetector : Detector {

    override val type = SensitiveType.URL

    override fun find(normalized: String): Sequence<Candidate> =
        URL_REGEX.findAll(normalized).mapNotNull { match ->
            var end = match.range.last
            while (end >= match.range.first && normalized[end] in TRAILING_PUNCTUATION) end--
            val range = match.range.first..end
            if (range.isEmpty()) null else Candidate(range)
        }

    /**
     * Keeps the scheme and host, masks everything that identifies *what* was
     * fetched: path segments, query values, and the fragment.
     *
     * Query **keys** survive on purpose. Destroying the query wholesale makes
     * the output unreadable, and the key is what tells the reader which piece of
     * information was removed — which is the whole difference between a redactor
     * and a delete key.
     *
     * Returns `null` — releasing the span — when the link carries no path, query
     * or fragment. A bare `https://example.com` hides nothing, so claiming it
     * would inflate the "links found" count and mangle the text for no gain.
     * Credentials in the authority are the one exception: those are masked, and
     * a URL that has them is never released.
     *
     * Parsed on the folded copy and copied out of the raw one. A link typed on a
     * CJK keyboard, `ｈｔｔｐｓ：／／ｅｘａｍｐｌｅ．ｃｏｍ／ａｃｃｏｕｎｔ`, has no
     * ASCII `/` for a raw search to find, so the whole path would read as part
     * of the host and stay in the clear. Every piece that survives — scheme,
     * host, the separators, a query key — is copied in the characters the user
     * wrote; only the masked pieces are new.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String? {
        val hidden = policy.maskChar.toString().repeat(SEGMENT_WIDTH)
        val scanned = ScanText.of(original)
        val s = scanned.scan

        /** The raw text behind the scan span from [from] to before [until]. */
        fun raw(from: Int, until: Int): String = original.substring(scanned.start(from), scanned.start(until))

        val schemeEnd = s.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        var authorityEnd = s.length
        for (index in schemeEnd until s.length) {
            val ch = s[index]
            if (ch == '/' || ch == '?' || ch == '#') {
                authorityEnd = index
                break
            }
        }
        val fragmentStart = s.indexOf('#', authorityEnd).let { if (it >= 0) it else s.length }
        val queryStart = s.indexOf('?', authorityEnd).let { if (it in 0 until fragmentStart) it else fragmentStart }
        val hasQuery = queryStart < fragmentStart
        val hasFragment = fragmentStart < s.length

        val userInfoEnd = s.lastIndexOf('@', authorityEnd - 1).let { if (it >= schemeEnd) it else -1 }
        val hasUserInfo = userInfoEnd >= 0
        val path = s.substring(authorityEnd, queryStart)
        val hasPath = path.isNotEmpty() && path != "/"
        val queryEmpty = !hasQuery || queryStart + 1 == fragmentStart
        val fragmentEmpty = !hasFragment || fragmentStart + 1 == s.length
        if (!hasPath && queryEmpty && fragmentEmpty && !hasUserInfo) return null

        return buildString {
            append(raw(0, schemeEnd))
            // Keep the '@' so the reader can see credentials were there at all.
            if (hasUserInfo) append(hidden)
            append(raw(if (hasUserInfo) userInfoEnd else schemeEnd, authorityEnd))

            // Path: every '/' as written, every segment between them collapsed.
            var inSegment = false
            for (index in authorityEnd until queryStart) {
                if (s[index] == '/') {
                    append(raw(index, index + 1))
                    inSegment = false
                } else if (!inSegment) {
                    append(hidden)
                    inSegment = true
                }
            }

            // Query: keys survive, so the reader can tell what was removed.
            // `usp=sharing` becomes `usp=****`; a bare value with no key goes.
            if (hasQuery) {
                append(raw(queryStart, queryStart + 1))
                var pairStart = queryStart + 1
                while (pairStart <= fragmentStart) {
                    var pairEnd = pairStart
                    while (pairEnd < fragmentStart && s[pairEnd] != '&') pairEnd++
                    if (pairEnd > pairStart) {
                        val equals = s.indexOf('=', pairStart).let { if (it in pairStart until pairEnd) it else -1 }
                        if (equals < 0) append(hidden) else append(raw(pairStart, equals + 1)).append(hidden)
                    }
                    if (pairEnd < fragmentStart) append(raw(pairEnd, pairEnd + 1))
                    pairStart = pairEnd + 1
                }
            }

            if (hasFragment) {
                append(raw(fragmentStart, fragmentStart + 1))
                if (!fragmentEmpty) append(hidden)
            }
        }
    }
}
