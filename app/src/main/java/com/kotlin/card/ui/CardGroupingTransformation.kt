package com.kotlin.card.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Displays a digit string grouped into 4-4-4-4 blocks while keeping the raw,
 * digits-only value intact. The offset mapping accounts for the inserted spaces
 * so the cursor stays put while editing.
 */
internal object CardGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = buildString {
            digits.forEachIndexed { index, c ->
                if (index != 0 && index % 4 == 0) append(' ')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                return offset + (offset - 1) / 4
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                return offset - offset / 5
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
