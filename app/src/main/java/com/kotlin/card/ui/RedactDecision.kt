package com.kotlin.card.ui

import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.RedactResult
import com.kotlin.card.filter.Redactor

/**
 * Selections larger than this are refused outright. A PROCESS_TEXT payload
 * arrives over a Binder transaction and the redacted answer has to go back the
 * same way; failing with a sentence beats a TransactionTooLargeException raised
 * inside somebody else's app.
 */
const val MAX_INBOUND_CHARS = 200 * 1024

/** Above this, Replace is disabled and Copy is offered instead. */
const val MAX_REPLACE_CHARS = 300 * 1024

/**
 * Everything the redaction sheet needs to draw itself, derived from the incoming
 * selection alone.
 *
 * Deliberately Android-free and computed by a pure function so the sheet's
 * decisions — when Replace is offered, when the size guards trip, what the
 * status line says — are unit-testable on the JVM. Tapping the button is thin
 * Compose wiring; *whether the button should be there at all* is the part with
 * real consequences, because getting it wrong either corrupts text in another
 * app or silently does nothing.
 */
data class RedactDecision(
    val result: RedactResult,
    /** The selection was too large to even scan. */
    val oversized: Boolean,
    /** The host app told us its text field is not editable. */
    val readOnly: Boolean,
    /** The redacted text is too large to hand back over a Binder. */
    val tooLongToReplace: Boolean
) {
    val found: Boolean get() = result.counts.isNotEmpty()

    /**
     * Replace is offered only when there is something to replace *and* handing
     * the text back is safe. Every guard fails towards Copy, which cannot
     * corrupt the other app's content.
     */
    val canReplace: Boolean get() = found && !oversized && !readOnly && !tooLongToReplace

    val canCopy: Boolean get() = !oversized
}

/** Decide what the sheet should show for [selection]. */
fun decideRedaction(
    selection: String,
    readOnly: Boolean,
    policy: MaskPolicy
): RedactDecision {
    val oversized = selection.length > MAX_INBOUND_CHARS
    val result =
        if (oversized) RedactResult(selection, emptyMap())
        else Redactor.redactAllInText(selection, policy)
    return RedactDecision(
        result = result,
        oversized = oversized,
        readOnly = readOnly,
        tooLongToReplace = result.output.length > MAX_REPLACE_CHARS
    )
}
