package com.kotlin.card.filter

/**
 * The kinds of sensitive value the redactor knows how to find.
 *
 * [priority] is the resolution order when two detectors claim overlapping text:
 * lower wins, and a claimed span is never partially re-claimed. The ordering
 * invariant is that a *container* type must outrank the types it can contain —
 * a JWT's base64 segments look like emails, IPs and cards; an IBAN contains a
 * card-length digit run; a Malaysian IC contains one too — so SECRET and URL sit
 * at the top and CARD, the catch-all numeric run, sits second from the bottom.
 *
 * CARD running late is also what makes the change monotone: everything ahead of
 * it either masks the span completely or explicitly declines it, so nothing that
 * is masked today comes back unmasked.
 */
enum class SensitiveType(
    val priority: Int,
    /** Singular noun for the "n found" summary line. */
    val label: String,
    /** Plural of [label]. Irregular enough to be worth spelling out. */
    val plural: String
) {
    SECRET(1, "secret", "secrets"),
    URL(2, "link", "links"),
    EMAIL(3, "email", "emails"),
    IBAN(4, "IBAN", "IBANs"),
    MY_NRIC(5, "IC", "ICs"),
    PHONE(6, "phone", "phones"),
    CARD(7, "card", "cards"),
    IPV4(8, "IP", "IPs")
}
