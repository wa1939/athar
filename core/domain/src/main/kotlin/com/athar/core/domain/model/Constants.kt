package com.athar.core.domain.model

/**
 * Synthetic "manual" account id — every manual transaction (entered via the Today FAB)
 * is attached to this account. Replaced once real account CRUD lands (ticket S-20).
 *
 * Kept stable so existing transactions keep referring to it after the migration.
 */
const val MANUAL_ACCOUNT_ID: String = "acc-manual"
