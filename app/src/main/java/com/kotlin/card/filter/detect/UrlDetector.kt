package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
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
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String? {
        val hidden = policy.maskChar.toString().repeat(SEGMENT_WIDTH)

        val schemeEnd = original.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        var authorityEnd = original.length
        for (index in schemeEnd until original.length) {
            val ch = original[index]
            if (ch == '/' || ch == '?' || ch == '#') {
                authorityEnd = index
                break
            }
        }
        val scheme = original.substring(0, schemeEnd)
        val authority = original.substring(schemeEnd, authorityEnd)
        val rest = original.substring(authorityEnd)

        val fragmentStart = rest.indexOf('#')
        val fragment = if (fragmentStart >= 0) rest.substring(fragmentStart + 1) else null
        val beforeFragment = if (fragmentStart >= 0) rest.substring(0, fragmentStart) else rest
        val queryStart = beforeFragment.indexOf('?')
        val query = if (queryStart >= 0) beforeFragment.substring(queryStart + 1) else null
        val path = if (queryStart >= 0) beforeFragment.substring(0, queryStart) else beforeFragment

        val userInfoEnd = authority.lastIndexOf('@')
        val hasUserInfo = userInfoEnd >= 0
        val hasPath = path.isNotEmpty() && path != "/"
        if (!hasPath && query.isNullOrEmpty() && fragment.isNullOrEmpty() && !hasUserInfo) return null

        return buildString {
            append(scheme)
            // Keep the '@' so the reader can see credentials were there at all.
            if (hasUserInfo) append(hidden)
            append(authority.substring(userInfoEnd.coerceAtLeast(0)))
            append(path.split('/').joinToString("/") { if (it.isEmpty()) it else hidden })
            if (query != null) {
                append('?')
                append(query.split('&').joinToString("&") { pair -> maskQueryPair(pair, hidden) })
            }
            if (fragment != null) {
                append('#')
                if (fragment.isNotEmpty()) append(hidden)
            }
        }
    }
}

/** `usp=sharing` becomes `usp=****`; a bare value with no key goes entirely. */
private fun maskQueryPair(pair: String, hidden: String): String {
    if (pair.isEmpty()) return pair
    val equals = pair.indexOf('=')
    return if (equals < 0) hidden else pair.substring(0, equals + 1) + hidden
}
