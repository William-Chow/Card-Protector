package com.kotlin.card.filter.detect

import com.kotlin.card.filter.Candidate
import com.kotlin.card.filter.Detector
import com.kotlin.card.filter.MaskPolicy
import com.kotlin.card.filter.SensitiveType

/**
 * One anchored secret shape.
 *
 * [group] is which capture group is the thing to hide — group 0 for a token that
 * is sensitive end to end, a later group where only part of the match is (a
 * `Bearer` header's token, the right-hand side of `password: …`).
 *
 * [keepPrefix] is how many leading characters of that span survive, so the
 * output still says *what kind* of credential was removed. Knowing a `sk-` key
 * was there is useful; knowing which one is not.
 */
private class SecretRule(val regex: Regex, val group: Int, val keepPrefix: Int)

/**
 * Anchored prefixes only — no entropy scanner, no generic base64 or long-hex
 * rule. Those eat git SHAs, UUIDs, ETags and build identifiers, and the anchored
 * table plus one `key = value` rule gets most of the value at near-zero cost.
 *
 * `tac` in the key-value rule is deliberate: it is the Malaysian term for a
 * banking one-time code, and it is what those SMSes actually say.
 */
private val SECRET_RULES = listOf(
    // JSON Web Token
    SecretRule(Regex("""\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b"""), 0, 3),
    // AWS access key id
    SecretRule(Regex("""\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"""), 0, 4),
    // Google API key
    SecretRule(Regex("""\bAIza[0-9A-Za-z_\-]{35}\b"""), 0, 4),
    // GitHub personal access / OAuth tokens
    SecretRule(Regex("""\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,}\b"""), 0, 4),
    // OpenAI / Anthropic keys
    SecretRule(Regex("""\bsk-(?:ant-)?[A-Za-z0-9_\-]{20,}\b"""), 0, 3),
    // Slack tokens
    SecretRule(Regex("""\bxox[abposr]-[A-Za-z0-9\-]{10,}\b"""), 0, 5),
    // Stripe keys
    SecretRule(Regex("""\b(?:sk|pk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\b"""), 0, 8),
    // Authorization header — the scheme stays, the token goes
    SecretRule(Regex("""\bBearer\s+([A-Za-z0-9._\-+/=]{16,})"""), 1, 0),
    // PEM private key block, header and footer included
    SecretRule(
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""),
        0,
        0
    ),
    // key = value / key: value. The literal separator is what stops this eating
    // prose — "a token gesture" has no `=` or `:` after it and never matches.
    SecretRule(
        Regex(
            """(?i)\b(?:password|passwd|pwd|api[_-]?key|secret|token|otp|tac)\b\s*[=:]\s*(\S{4,})"""
        ),
        1,
        0
    )
)

/** How many glyphs a secret body collapses to, whatever its real length. */
private const val SECRET_WIDTH = 8

/**
 * API keys, tokens and passwords.
 *
 * Runs first, and has to: a JWT's base64 segments trip the email, IP and card
 * patterns simultaneously, so anything less than top priority would leave a
 * token half-masked in three different ways.
 */
object SecretDetector : Detector {

    override val type = SensitiveType.SECRET

    override fun find(normalized: String): Sequence<Candidate> = sequence {
        for (rule in SECRET_RULES) {
            for (match in rule.regex.findAll(normalized)) {
                val range = match.groups[rule.group]?.range ?: continue
                if (range.isEmpty()) continue
                yield(Candidate(range, keepPrefix = rule.keepPrefix))
            }
        }
    }

    /**
     * Brand marker, then a fixed-width run of mask glyphs. The body collapses
     * rather than masking 1:1 because the *length* of a credential is itself a
     * fingerprint of which service issued it.
     */
    override fun redact(original: String, candidate: Candidate, policy: MaskPolicy): String =
        original.take(candidate.keepPrefix) +
            policy.maskChar.toString().repeat(SECRET_WIDTH)
}
