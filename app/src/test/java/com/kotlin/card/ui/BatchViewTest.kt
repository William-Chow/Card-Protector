package com.kotlin.card.ui

import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch mode's one decision with consequences: whether Copy, Share and the CTA
 * may act on what is on screen. Before this was pinned, a long paste followed
 * by a quick tap on Copy could copy the masked form of the *previous* paste.
 */
class BatchViewTest {

    private val policy = MaskPolicy('*')
    private val card = "pay 4111 1111 1111 1111 today"
    private val otherCard = "card 5500 0000 0000 0004 please"

    private fun scan(text: String, scanPolicy: MaskPolicy = policy) =
        BatchScan(text, scanPolicy, Redactor.redactAllInText(text, scanPolicy))

    @Test
    fun `a short input scanned inline is ready as soon as it is scanned`() {
        val view = batchView(card, policy, Redactor.redactAllInText(card, policy), async = null)
        assertTrue(view.ready)
        assertEquals("pay **** **** **** 1111 today", view.result.output)
    }

    @Test
    fun `a long input is ready once the background scan matches the field`() {
        val view = batchView(card, policy, sync = null, async = scan(card))
        assertTrue(view.ready)
    }

    @Test
    fun `a scan of the previous paste stays on screen but cannot be acted on`() {
        val view = batchView(otherCard, policy, sync = null, async = scan(card))
        assertFalse("stale scan was actionable", view.ready)
        assertEquals("the last result stays visible", scan(card).result.output, view.result.output)
    }

    @Test
    fun `a scan under a different mask is stale too`() {
        val view = batchView(card, policy, sync = null, async = scan(card, MaskPolicy('#')))
        assertFalse(view.ready)
    }

    @Test
    fun `nothing scanned yet shows nothing and enables nothing`() {
        val view = batchView(card, policy, sync = null, async = null)
        assertFalse(view.ready)
        assertFalse(view.found)
        assertEquals("", view.result.output)
    }

    @Test
    fun `a current scan that found nothing is not actionable`() {
        val text = "nothing sensitive here"
        assertFalse(batchView(text, policy, sync = null, async = scan(text)).ready)
        assertFalse(batchView(text, policy, Redactor.redactAllInText(text, policy), async = null).ready)
    }
}
