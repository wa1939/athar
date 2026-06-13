package com.athar.core.domain.repo

import java.io.OutputStream

/**
 * Exports a redacted diagnostics report for user-approved support triage.
 *
 * Privacy contract: the report contains parse counts, pseudonymous sender/body hashes,
 * body-shape flags, status groups, and redacted parser-error summaries. It must never
 * include raw SMS bodies, raw sender values, balances, card numbers, account numbers,
 * or transaction rows.
 */
interface SupportDiagnosticsExportTrigger {
    suspend fun exportDiagnostics(out: OutputStream): SupportDiagnosticsExportResult
}

sealed interface SupportDiagnosticsExportResult {
    data class Done(
        val auditRows: Int,
        val parsed: Int,
        val failed: Int,
        val ignored: Int,
    ) : SupportDiagnosticsExportResult

    data class Failed(val reason: String) : SupportDiagnosticsExportResult
}
