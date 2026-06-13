package com.athar.core.domain.model

/**
 * Resolves an ingestion event to one configured account.
 *
 * Account `smsSenders` are treated as routing aliases. Text aliases match sender names;
 * numeric aliases match card/account tails inside the message body or parser counterparty.
 * If matching is ambiguous, the caller should fall back to the manual seed account rather
 * than guessing and corrupting net worth.
 */
object AccountRouting {

    fun resolve(
        accounts: List<Account>,
        sender: String,
        body: String,
        counterparty: String?,
    ): Account? {
        val active = accounts.filterNot { it.archived }
        if (active.isEmpty()) return null

        val numericMatches = active.filter { account ->
            account.routingAliases().any { alias ->
                alias.isNumericTail() &&
                    (containsNumericTail(body, alias) || counterparty?.let { containsNumericTail(it, alias) } == true)
            }
        }
        if (numericMatches.size == 1) return numericMatches.single()
        if (numericMatches.size > 1) return null

        val normalizedSender = sender.normalizeRoutingText()
        val senderMatches = active.filter { account ->
            account.routingAliases().any { alias ->
                !alias.isNumericTail() && senderAliasMatches(normalizedSender, alias)
            }
        }
        return senderMatches.singleOrNull()
    }

    fun normalizeAliases(raw: String): List<String> =
        raw.split(',', '\n', ';')
            .map { it.normalizeRoutingText() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Account.routingAliases(): List<String> =
        smsSenders.map { it.normalizeRoutingText() }.filter { it.isNotBlank() }

    private fun senderAliasMatches(sender: String, alias: String): Boolean =
        sender == alias || (alias.length >= 4 && sender.contains(alias))

    private fun String.isNumericTail(): Boolean =
        length in 3..6 && all { it.isDigit() }

    private fun containsNumericTail(text: String, tail: String): Boolean =
        NumericTailRegex(tail).containsMatchIn(text.normalizeArabicDigits())

    private fun NumericTailRegex(tail: String): Regex =
        Regex("(?<!\\d)${Regex.escape(tail)}(?!\\d)")

    private fun String.normalizeRoutingText(): String =
        normalizeArabicDigits()
            .lowercase()
            .trim()
            .replace(WhitespaceRegex, " ")

    private fun String.normalizeArabicDigits(): String = buildString(length) {
        this@normalizeArabicDigits.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                },
            )
        }
    }

    private val WhitespaceRegex = Regex("\\s+")
}
