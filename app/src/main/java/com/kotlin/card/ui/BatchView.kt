package com.kotlin.card.ui

import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.RedactResult

/** A background scan, and the input it was computed from. */
class BatchScan(val text: String, val policy: MaskPolicy, val result: RedactResult)

/**
 * What the batch screen shows, and whether Copy, Share and the CTA may act on it.
 *
 * [result] can be stale; [ready] never is. Until a long paste's background scan
 * catches up, the last scan is the masked form of text the user has since
 * changed: a paragraph they just deleted, or a different paste altogether. It
 * stays on screen so a long paste does not flicker on every keystroke, but the
 * actions wait for the scan that matches the field.
 */
data class BatchView(val result: RedactResult, val ready: Boolean) {
    val found: Boolean get() = result.counts.isNotEmpty()
}

/** The "nothing scanned yet" result, so the async path has something to show. */
private val EMPTY_RESULT = RedactResult("", emptyMap())

/**
 * Decide what the batch screen shows for [text] under [policy], given the
 * synchronous scan of a short input ([sync], `null` when the input is too long
 * to scan inline) and the latest background scan ([async]).
 *
 * Android-free for the same reason as [decideRedaction]: the part with
 * consequences — whether the buttons hand somebody stale text — is testable on
 * the JVM, and the Compose code that calls this is thin wiring.
 */
fun batchView(text: String, policy: MaskPolicy, sync: RedactResult?, async: BatchScan?): BatchView {
    val current = sync ?: async?.takeIf { it.text == text && it.policy == policy }?.result
    val shown = current ?: async?.result ?: EMPTY_RESULT
    return BatchView(shown, ready = current != null && current.counts.isNotEmpty())
}
