package com.athar.core.domain.model

/** Master Brief §4.5 — Transaction.type. */
enum class TxType { EXPENSE, INCOME, TRANSFER }

/** Master Brief §4.5 — Transaction.source. */
enum class IngestSource { MANUAL, SMS, NOTIFICATION, SHARE, IMPORT, RECURRING }

/** Master Brief §4.5 — Transaction.status. */
enum class TxStatus { PENDING, CONFIRMED, DISMISSED }

/** Master Brief §4.5 — Category.kind. */
enum class CategoryKind { EXPENSE, INCOME }

/** Master Brief §4.5 — Account.type. */
enum class AccountType { DEBIT, CREDIT, CASH, OTHER }

/** Master Brief §4.7 — CategoryRule.patternType. */
enum class PatternType { SUBSTRING, REGEX, EXACT }

/** Source of an auto-categorization decision. Master Brief §4.7. */
enum class CategorySource { RULE_EXACT, RULE_SUBSTRING, RULE_REGEX, CLASSIFIER, UNKNOWN }

/** SMS audit parse status. Master Brief §4.5. */
enum class SmsParseStatus { PARSED, FAILED, IGNORED }
