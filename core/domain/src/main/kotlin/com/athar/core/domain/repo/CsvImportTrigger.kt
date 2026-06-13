package com.athar.core.domain.repo

import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.TxType
import java.io.InputStream
import java.math.BigDecimal

/**
 * Parses statement-style transaction files and bulk-inserts them as confirmed manual entries.
 *
 * Supported delimited text columns (CSV/TSV header row, case-insensitive, any order):
 *   - delimiters — comma, semicolon, or tab; quoted delimiters and `""` escapes are supported
 *   - **date** — `YYYY-MM-DD`, `DD/MM/YYYY`, or `MM/DD/YYYY`
 *   - **vendor**, **merchant**, **description**, **details**, **narrative**, or Arabic equivalents — text
 *   - **amount** — positive decimal for Athar/TMOAP exports, or signed decimal for statement-like files
 *   - **debit** / **credit** — split amount columns; debit imports as EXPENSE, credit imports as INCOME
 *   - **type** — `EXPENSE`/`INCOME`/`TRANSFER`, debit/credit synonyms, or marker columns such as `D/C`
 *   - **currency** — optional ISO-4217 code; defaults to SAR when missing
 *   - **category** — matches `name` or `nameAr` (case-insensitive); if missing, transaction lands uncategorized
 *   - **notes** — optional
 *
 * If auto-detection cannot recognize a CSV/TSV header row, callers can pass
 * [CsvImportColumnMapping] after showing the user the parsed header list from
 * [CsvImportPreviewResult.MappingRequired].
 *
 * Supported OFX/QFX fields:
 *   - `DTPOSTED` / `DTUSER` — transaction date
 *   - `TRNAMT` — signed amount; negative imports as expense unless `TRNTYPE` says otherwise
 *   - `NAME`, `PAYEE`, or `MEMO` — merchant/counterparty
 *   - `TRNTYPE` — expense, income, or transfer hint
 *   - `CURDEF` / `CURSYM` — optional ISO-4217 currency; defaults to SAR when missing
 *   - `FITID` — stable import reference when present
 *
 * Supported MT940 fields:
 *   - `:61:` — date, debit/credit mark, amount, transaction code, and optional reference
 *   - `:86:` — merchant/counterparty details
 *   - `:60F:` / `:60M:` / `:62F:` / `:62M:` — optional statement currency; defaults to SAR when missing
 *
 * The importer is conservative: a row with a parse error or a row already imported
 * for the selected account is skipped and counted in [CsvImportResult.skipped]
 * rather than aborting the whole batch.
 * Imported rows are assigned to [accountId]; callers that do not expose account selection
 * keep using [MANUAL_ACCOUNT_ID].
 *
 * Master Brief / Backlog M-14 and roadmap G-12.
 */
interface CsvImportTrigger {
    suspend fun preview(
        input: InputStream,
        accountId: String = MANUAL_ACCOUNT_ID,
        mapping: CsvImportColumnMapping? = null,
        rowDecisions: List<CsvImportRowDecision> = emptyList(),
        rowEdits: List<CsvImportRowEdit> = emptyList(),
    ): CsvImportPreviewResult

    suspend fun import(
        input: InputStream,
        accountId: String = MANUAL_ACCOUNT_ID,
        mapping: CsvImportColumnMapping? = null,
        rowDecisions: List<CsvImportRowDecision> = emptyList(),
        rowEdits: List<CsvImportRowEdit> = emptyList(),
    ): CsvImportResult
}

sealed interface CsvImportPreviewResult {
    data class Done(val preview: CsvImportPreview) : CsvImportPreviewResult
    data class MappingRequired(val columns: List<String>, val reason: String) : CsvImportPreviewResult
    data class Failed(val reason: String) : CsvImportPreviewResult
}

data class CsvImportPreview(
    val importable: Int,
    val skipped: Int,
    val columns: CsvImportDetectedColumns,
    val availableColumns: List<String> = emptyList(),
    val currencySummaries: List<CsvImportCurrencySummary> = emptyList(),
    val sampleRows: List<CsvImportPreviewRow>,
    val skippedRows: List<CsvImportSkippedRow>,
)

data class CsvImportCurrencySummary(
    val currency: String,
    val rows: Int,
    val expenseTotal: BigDecimal,
    val incomeTotal: BigDecimal,
    val transferTotal: BigDecimal,
)

data class CsvImportColumnMapping(
    val date: String? = null,
    val merchant: String? = null,
    val amount: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val type: String? = null,
    val notes: String? = null,
) {
    fun valueFor(role: CsvImportColumnRole): String? = when (role) {
        CsvImportColumnRole.DATE -> date
        CsvImportColumnRole.MERCHANT -> merchant
        CsvImportColumnRole.AMOUNT -> amount
        CsvImportColumnRole.DEBIT -> debit
        CsvImportColumnRole.CREDIT -> credit
        CsvImportColumnRole.CURRENCY -> currency
        CsvImportColumnRole.CATEGORY -> category
        CsvImportColumnRole.TYPE -> type
        CsvImportColumnRole.NOTES -> notes
    }

    fun with(role: CsvImportColumnRole, column: String?): CsvImportColumnMapping = when (role) {
        CsvImportColumnRole.DATE -> copy(date = column)
        CsvImportColumnRole.MERCHANT -> copy(merchant = column)
        CsvImportColumnRole.AMOUNT -> copy(amount = column)
        CsvImportColumnRole.DEBIT -> copy(debit = column)
        CsvImportColumnRole.CREDIT -> copy(credit = column)
        CsvImportColumnRole.CURRENCY -> copy(currency = column)
        CsvImportColumnRole.CATEGORY -> copy(category = column)
        CsvImportColumnRole.TYPE -> copy(type = column)
        CsvImportColumnRole.NOTES -> copy(notes = column)
    }
}

data class CsvImportRowDecision(
    val rowNumber: Int,
    val shouldImport: Boolean,
)

data class CsvImportRowEdit(
    val rowNumber: Int,
    val date: String? = null,
    val merchant: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val type: TxType? = null,
    val category: String? = null,
    val notes: String? = null,
)

enum class CsvImportColumnRole {
    DATE,
    MERCHANT,
    AMOUNT,
    DEBIT,
    CREDIT,
    CURRENCY,
    CATEGORY,
    TYPE,
    NOTES,
}

data class CsvImportDetectedColumns(
    val date: String,
    val merchant: String,
    val amount: String?,
    val debit: String?,
    val credit: String?,
    val currency: String?,
    val category: String?,
    val type: String?,
    val notes: String?,
)

data class CsvImportPreviewRow(
    val rowNumber: Int,
    val date: String,
    val merchant: String,
    val amount: String,
    val currency: String,
    val type: com.athar.core.domain.model.TxType,
    val category: String?,
    val notes: String?,
    val included: Boolean = true,
    val edited: Boolean = false,
)

data class CsvImportSkippedRow(
    val rowNumber: Int,
    val reason: String,
)

sealed interface CsvImportResult {
    data class Done(val imported: Int, val skipped: Int) : CsvImportResult
    data class Failed(val reason: String) : CsvImportResult
}
