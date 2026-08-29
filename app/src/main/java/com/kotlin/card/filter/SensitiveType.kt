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
 * CARD running late is what keeps the *reclassifications* safe: everything ahead
 * of it either masks its own span completely or explicitly declines it, so a
 * value that changes hands is not handed back readable.
 *
 * **Priority order alone does not make the engine monotone, and the earlier
 * version of this comment claiming it did was wrong.** It reasons about spans
 * that nest — an IC inside a card run — and says nothing about spans that
 * *overlap*: two values one space or one dash apart form a single card run, so
 * the card match runs across the boundary of the higher-priority claim rather
 * than inside it. Ordering decides who wins a contested span; what happens to
 * the part of a span nobody won is the resolver's problem, and the answer has to
 * be "re-offer it", not "drop it". See `Redactor.redactAllInText`.
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
