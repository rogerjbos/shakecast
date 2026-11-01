package com.rogerbos.shakecast.domain.ads

/**
 * Regexes ported from podcast3r/skip_ads.py. Python's \w is Unicode-aware, so
 * it is spelled out as [\p{L}\p{N}_] here; Android's ICU regex engine does not
 * support the (?U) flag.
 */
object AdPatterns {
    private const val W = "[\\p{L}\\p{N}_]"

    val URL: Regex = Regex(
        "\\b[a-z0-9][-a-z0-9]*\\.(com|org|net|io|co|ly|ca|us|uk)\\b" +
            "|\\bdot\\s+(com|org|net|io|co|ly|ca|us|uk)\\b",
        RegexOption.IGNORE_CASE
    )

    val SPOKEN_URL: Regex = Regex(
        "\\b[a-z0-9][-a-z0-9]*\\.$W+\\s+slash\\s+$W+",
        RegexOption.IGNORE_CASE
    )

    val SPELLED_URL: Regex = Regex(
        "[a-z](-[a-z]){3,}\\s*(dot\\s+com|\\.com)",
        RegexOption.IGNORE_CASE
    )

    val TOLLFREE: Regex = Regex(
        "\\b1[- ]?8[0-9]{2}[- ]?$W{3,}",
        RegexOption.IGNORE_CASE
    )
}
