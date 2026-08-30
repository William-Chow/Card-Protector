package com.kotlin.card.ui

import android.content.Context
import com.kotlin.card.R
import com.kotlin.card.filter.SensitiveType

/**
 * The `<plurals>` resource that names each sensitive type in the "n found"
 * summary.
 *
 * A `when` over the enum rather than a map, so adding a type to
 * [SensitiveType] without giving it a name fails to compile instead of failing
 * at runtime in front of a user. Kotlin makes the `when` exhaustive here because
 * the result is used as an expression.
 */
private fun pluralsFor(type: SensitiveType): Int = when (type) {
    SensitiveType.SECRET -> R.plurals.type_secret
    SensitiveType.URL -> R.plurals.type_url
    SensitiveType.EMAIL -> R.plurals.type_email
    SensitiveType.IBAN -> R.plurals.type_iban
    SensitiveType.MY_NRIC -> R.plurals.type_nric
    SensitiveType.PHONE -> R.plurals.type_phone
    SensitiveType.CARD_EXPIRY -> R.plurals.type_expiry
    SensitiveType.CVV -> R.plurals.type_cvv
    SensitiveType.CARD -> R.plurals.type_card
    SensitiveType.IPV4 -> R.plurals.type_ip
}

/**
 * A labeller for `summarizeCounts`, backed by string resources.
 *
 * Plain function rather than a `@Composable`, because the summary is built in
 * both a composable (the batch counter) and a plain one (the sheet's title), and
 * `pluralStringResource` cannot be called from inside the non-composable lambda
 * `summarizeCounts` takes.
 */
fun typeLabeller(context: Context): (SensitiveType, Int) -> String = { type, count ->
    context.resources.getQuantityString(pluralsFor(type), count, count)
}
