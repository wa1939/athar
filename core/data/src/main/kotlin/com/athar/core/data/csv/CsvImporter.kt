package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CsvImportColumnMapping
import com.athar.core.domain.repo.CsvImportCurrencySummary
import com.athar.core.domain.repo.CsvImportDetectedColumns
import com.athar.core.domain.repo.CsvImportPreview
import com.athar.core.domain.repo.CsvImportPreviewResult
import com.athar.core.domain.repo.CsvImportPreviewRow
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportRowDecision
import com.athar.core.domain.repo.CsvImportRowEdit
import com.athar.core.domain.repo.CsvImportSkippedRow
import com.athar.core.domain.repo.CsvImportTrigger
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CsvImporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : CsvImportTrigger {

    override suspend fun preview(
        input: InputStream,
        accountId: String,
        mapping: CsvImportColumnMapping?,
        rowDecisions: List<CsvImportRowDecision>,
        rowEdits: List<CsvImportRowEdit>,
    ): CsvImportPreviewResult {
        return when (val plan = buildPlan(input, accountId, mapping, rowDecisions, rowEdits)) {
            is CsvPlanResult.Done -> CsvImportPreviewResult.Done(plan.plan.toPreview())
            is CsvPlanResult.MappingRequired -> CsvImportPreviewResult.MappingRequired(
                columns = plan.columns,
                reason = plan.reason,
            )
            is CsvPlanResult.Failed -> CsvImportPreviewResult.Failed(plan.reason)
        }
    }

    override suspend fun import(
        input: InputStream,
        accountId: String,
        mapping: CsvImportColumnMapping?,
        rowDecisions: List<CsvImportRowDecision>,
        rowEdits: List<CsvImportRowEdit>,
    ): CsvImportResult {
        val plan = when (val result = buildPlan(input, accountId, mapping, rowDecisions, rowEdits)) {
            is CsvPlanResult.Done -> result.plan
            is CsvPlanResult.MappingRequired -> return CsvImportResult.Failed(result.reason)
            is CsvPlanResult.Failed -> return CsvImportResult.Failed(result.reason)
        }

        plan.transactions.forEach { transactions.upsert(it.transaction) }
        Timber.i("Statement import: imported=%d skipped=%d", plan.transactions.size, plan.skippedRows.size)
        return CsvImportResult.Done(imported = plan.transactions.size, skipped = plan.skippedRows.size)
    }

    private suspend fun buildPlan(
        input: InputStream,
        accountId: String = MANUAL_ACCOUNT_ID,
        mapping: CsvImportColumnMapping? = null,
        rowDecisions: List<CsvImportRowDecision> = emptyList(),
        rowEdits: List<CsvImportRowEdit> = emptyList(),
    ): CsvPlanResult {
        val text = runCatching { input.bufferedReader().use { it.readText() } }
            .getOrElse { return CsvPlanResult.Failed("Couldn't read import file: ${it.message}") }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvPlanResult.Failed("Empty import file.")

        val decisions = rowDecisions.shouldImportByRowNumber()
        val edits = rowEdits.byRowNumber()
        val categoryLookup = categoryLookup()

        if (StatementOfxMapper.looksLikeOfx(text)) {
            return buildOfxPlan(text, accountId, decisions, edits, categoryLookup)
        }

        if (StatementMt940Mapper.looksLikeMt940(text)) {
            return buildMt940Plan(text, accountId, decisions, edits, categoryLookup)
        }

        val format = detectCsvFormat(lines[0], mapping)
        if (format == null) {
            return CsvPlanResult.MappingRequired(
                columns = detectDelimitedHeader(lines[0]),
                reason = "Map date, merchant, and amount/debit/credit columns before previewing this file.",
            )
        }
        val header = format.header
        val columns = format.columns

        val existingImports = existingImportIdentities(accountId)
        val refs = StableImportRefBuilder(accountId = accountId, format = "csv")
        val now = clock.now()

        val transactions = mutableListOf<CsvPlanTransaction>()
        val previewRows = mutableListOf<CsvPlanTransaction>()
        val skippedRows = mutableListOf<CsvImportSkippedRow>()
        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val rowNumber = rowIndex + 2 // +1 for header, +1 to make 1-based
            val row = parseRow(line, format.delimiter)
            val mapped = mapRow(
                rowIndex = rowNumber,
                row = row,
                columns = columns,
                categoryLookup = categoryLookup,
                now = now,
                accountId = accountId,
            )
            if (mapped == null) {
                skippedRows += CsvImportSkippedRow(
                    rowNumber = rowNumber,
                    reason = "Unparseable row",
                )
                continue
            }
            val edited = when (val editResult = mapped.applyEdit(edits[rowNumber], categoryLookup)) {
                is RowEditResult.Done -> editResult.row
                is RowEditResult.Invalid -> {
                    skippedRows += CsvImportSkippedRow(
                        rowNumber = rowNumber,
                        reason = editResult.reason,
                    )
                    continue
                }
            }
            val identity = refs.nextForContent(edited.transaction)
            val transaction = edited.transaction.withStableSourceRef(identity.sourceRefId)
            addImportableOrDuplicateSkip(
                rowNumber = rowNumber,
                transaction = transaction,
                identity = identity,
                categoryPreview = edited.categoryPreview,
                edited = edited.edited,
                decisions = decisions,
                existingImports = existingImports,
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                columns = detectedColumns(header, columns),
                availableColumns = header,
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            ),
        )
    }

    private suspend fun buildOfxPlan(
        text: String,
        accountId: String,
        decisions: Map<Int, Boolean>,
        edits: Map<Int, CsvImportRowEdit>,
        categoryLookup: CategoryLookup,
    ): CsvPlanResult {
        val parsed = when (val result = StatementOfxMapper.parse(text)) {
            is StatementOfxParseResult.Done -> result
            is StatementOfxParseResult.Failed -> return CsvPlanResult.Failed(result.reason)
        }

        val now = clock.now()
        val existingImports = existingImportIdentities(accountId)
        val refs = StableImportRefBuilder(accountId = accountId, format = "ofx")
        val transactions = mutableListOf<CsvPlanTransaction>()
        val previewRows = mutableListOf<CsvPlanTransaction>()
        val skippedRows = parsed.skippedRowNumbers.map { skippedRowNumber ->
            CsvImportSkippedRow(
                rowNumber = skippedRowNumber,
                reason = "Unparseable OFX/QFX transaction",
            )
        }.toMutableList()

        parsed.rows.forEach { row ->
            val transactionWithoutRef = Transaction(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                type = row.type,
                amount = Money.of(row.amount, row.currency),
                date = row.date,
                occurredAt = null,
                merchant = row.merchant,
                merchantNormalized = row.merchant.lowercase().trim(),
                categoryId = null,
                notes = row.notes,
                source = IngestSource.IMPORT,
                sourceRefId = null,
                status = TxStatus.CONFIRMED,
                confidence = 1.0f,
                createdAt = now,
                updatedAt = now,
            )
            val mapped = CsvMappedTransaction(transactionWithoutRef, categoryPreview = null, edited = false)
            val edited = when (val editResult = mapped.applyEdit(edits[row.rowNumber], categoryLookup)) {
                is RowEditResult.Done -> editResult.row
                is RowEditResult.Invalid -> {
                    skippedRows += CsvImportSkippedRow(rowNumber = row.rowNumber, reason = editResult.reason)
                    return@forEach
                }
            }
            val identity = row.sourceRefId
                ?.let { refs.nextForExternalRef(it, edited.transaction) }
                ?: refs.nextForContent(edited.transaction)
            val transaction = edited.transaction.withStableSourceRef(identity.sourceRefId)
            addImportableOrDuplicateSkip(
                rowNumber = row.rowNumber,
                transaction = transaction,
                identity = identity,
                categoryPreview = edited.categoryPreview,
                edited = edited.edited,
                decisions = decisions,
                existingImports = existingImports,
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                columns = CsvImportDetectedColumns(
                    date = "OFX DTPOSTED",
                    merchant = "OFX NAME/MEMO",
                    amount = "OFX TRNAMT",
                    debit = null,
                    credit = null,
                    currency = "OFX CURDEF",
                    category = null,
                    type = "OFX TRNTYPE",
                    notes = "OFX MEMO/FITID",
                ),
                availableColumns = emptyList(),
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            ),
        )
    }

    private suspend fun buildMt940Plan(
        text: String,
        accountId: String,
        decisions: Map<Int, Boolean>,
        edits: Map<Int, CsvImportRowEdit>,
        categoryLookup: CategoryLookup,
    ): CsvPlanResult {
        val parsed = when (val result = StatementMt940Mapper.parse(text)) {
            is StatementMt940ParseResult.Done -> result
            is StatementMt940ParseResult.Failed -> return CsvPlanResult.Failed(result.reason)
        }

        val now = clock.now()
        val existingImports = existingImportIdentities(accountId)
        val refs = StableImportRefBuilder(accountId = accountId, format = "mt940")
        val transactions = mutableListOf<CsvPlanTransaction>()
        val previewRows = mutableListOf<CsvPlanTransaction>()
        val skippedRows = parsed.skippedRowNumbers.map { skippedRowNumber ->
            CsvImportSkippedRow(
                rowNumber = skippedRowNumber,
                reason = "Unparseable MT940 transaction",
            )
        }.toMutableList()

        parsed.rows.forEach { row ->
            val transactionWithoutRef = Transaction(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                type = row.type,
                amount = Money.of(row.amount, row.currency),
                date = row.date,
                occurredAt = null,
                merchant = row.merchant,
                merchantNormalized = row.merchant.lowercase().trim(),
                categoryId = null,
                notes = row.notes,
                source = IngestSource.IMPORT,
                sourceRefId = null,
                status = TxStatus.CONFIRMED,
                confidence = 1.0f,
                createdAt = now,
                updatedAt = now,
            )
            val mapped = CsvMappedTransaction(transactionWithoutRef, categoryPreview = null, edited = false)
            val edited = when (val editResult = mapped.applyEdit(edits[row.rowNumber], categoryLookup)) {
                is RowEditResult.Done -> editResult.row
                is RowEditResult.Invalid -> {
                    skippedRows += CsvImportSkippedRow(rowNumber = row.rowNumber, reason = editResult.reason)
                    return@forEach
                }
            }
            val identity = row.sourceRefId
                ?.let { refs.nextForExternalRef(it, edited.transaction) }
                ?: refs.nextForContent(edited.transaction)
            val transaction = edited.transaction.withStableSourceRef(identity.sourceRefId)
            addImportableOrDuplicateSkip(
                rowNumber = row.rowNumber,
                transaction = transaction,
                identity = identity,
                categoryPreview = edited.categoryPreview,
                edited = edited.edited,
                decisions = decisions,
                existingImports = existingImports,
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                columns = CsvImportDetectedColumns(
                    date = "MT940 :61: date",
                    merchant = "MT940 :86:",
                    amount = "MT940 :61: amount",
                    debit = null,
                    credit = null,
                    currency = "MT940 :60F:/:62F:",
                    category = null,
                    type = "MT940 debit/credit mark",
                    notes = "MT940 :61:/:86:",
                ),
                availableColumns = emptyList(),
                transactions = transactions,
                previewRows = previewRows,
                skippedRows = skippedRows,
            ),
        )
    }

    private fun mapRow(
        rowIndex: Int,
        row: List<String>,
        columns: StatementCsvColumns,
        categoryLookup: CategoryLookup,
        now: kotlinx.datetime.Instant,
        accountId: String,
    ): CsvMappedTransaction? {
        val mapped = StatementCsvMapper.map(row, columns) ?: run {
            Timber.w("CSV row %d skipped (unparseable statement row)", rowIndex)
            return null
        }

        val categoryId = mapped.category?.let(categoryLookup::categoryIdFor)

        return CsvMappedTransaction(
            transaction = Transaction(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                type = mapped.type,
                amount = Money.of(mapped.amount, mapped.currency),
                date = mapped.date,
                occurredAt = null,
                merchant = mapped.merchant,
                merchantNormalized = mapped.merchant.lowercase().trim(),
                categoryId = categoryId,
                notes = mapped.notes,
                source = IngestSource.IMPORT,
                sourceRefId = null,
                status = TxStatus.CONFIRMED,
                confidence = 1.0f,
                createdAt = now,
                updatedAt = now,
            ),
            categoryPreview = mapped.category,
            edited = false,
        )
    }

    private fun CsvImportPlan.toPreview(): CsvImportPreview = CsvImportPreview(
        importable = transactions.size,
        skipped = skippedRows.size,
        columns = columns,
        availableColumns = availableColumns,
        currencySummaries = transactions.currencySummaries(),
        sampleRows = previewRows.take(PREVIEW_ROW_LIMIT).map { row ->
            CsvImportPreviewRow(
                rowNumber = row.rowNumber,
                date = row.transaction.date.toString(),
                merchant = row.transaction.merchant,
                amount = row.transaction.amount.amount.toPlainString(),
                currency = row.transaction.amount.currency,
                type = row.transaction.type,
                category = row.categoryPreview,
                notes = row.transaction.notes,
                included = row.included,
                edited = row.edited,
            )
        },
        skippedRows = skippedRows.take(PREVIEW_ROW_LIMIT),
    )

    private fun List<CsvPlanTransaction>.currencySummaries(): List<CsvImportCurrencySummary> =
        groupBy { it.transaction.amount.currency.uppercase().trim() }
            .map { (currency, rows) ->
                CsvImportCurrencySummary(
                    currency = currency,
                    rows = rows.size,
                    expenseTotal = rows.totalFor(TxType.EXPENSE),
                    incomeTotal = rows.totalFor(TxType.INCOME),
                    transferTotal = rows.totalFor(TxType.TRANSFER),
                )
            }
            .sortedBy { it.currency }

    private fun List<CsvPlanTransaction>.totalFor(type: TxType): BigDecimal =
        filter { it.transaction.type == type }
            .fold(BigDecimal.ZERO) { total, row -> total + row.transaction.amount.amount }

    private suspend fun categoryLookup(): CategoryLookup {
        val rows = categories.observeAll(kind = null, includeArchived = false).first()
        return CategoryLookup(
            byId = rows.associateBy { it.id.lowercase().trim() },
            byName = rows.associateBy { it.name.lowercase().trim() },
            byAr = rows.associateBy { it.nameAr.trim() },
        )
    }

    private fun CsvMappedTransaction.applyEdit(
        edit: CsvImportRowEdit?,
        categoryLookup: CategoryLookup,
    ): RowEditResult {
        if (edit == null) return RowEditResult.Done(this)

        val nextDate = edit.date?.trim()?.let { raw ->
            raw.takeIf { it.isNotBlank() } ?: return RowEditResult.Invalid("Invalid edited date")
            runCatching { LocalDate.parse(raw) }.getOrNull()
                ?: return RowEditResult.Invalid("Invalid edited date")
        } ?: transaction.date

        val nextMerchant = edit.merchant?.trim()?.let { raw ->
            raw.takeIf { it.isNotBlank() } ?: return RowEditResult.Invalid("Edited merchant is blank")
        } ?: transaction.merchant

        val nextAmount = edit.amount?.trim()?.let { raw ->
            raw.takeIf { it.isNotBlank() } ?: return RowEditResult.Invalid("Invalid edited amount")
            val parsed = runCatching { BigDecimal(raw.replace(",", "")) }.getOrNull()
                ?: return RowEditResult.Invalid("Invalid edited amount")
            parsed.abs().takeIf { it.signum() > 0 }
                ?: return RowEditResult.Invalid("Invalid edited amount")
        } ?: transaction.amount.amount

        val nextCurrency = edit.currency?.trim()?.let { raw ->
            raw.takeIf { it.isNotBlank() } ?: return RowEditResult.Invalid("Invalid edited currency")
            raw.uppercase().takeIf { ISO_CURRENCY_REGEX.matches(it) }
                ?: return RowEditResult.Invalid("Invalid edited currency")
        } ?: transaction.amount.currency

        val categoryOverride = edit.category?.let { raw ->
            categoryLookup.resolve(raw) ?: return RowEditResult.Invalid("Unknown edited category")
        }
        val editedNotes = edit.notes

        val nextTransaction = transaction.copy(
            date = nextDate,
            merchant = nextMerchant,
            merchantNormalized = nextMerchant.lowercase().trim(),
            amount = Money.of(nextAmount, nextCurrency),
            type = edit.type ?: transaction.type,
            categoryId = if (categoryOverride != null) categoryOverride.id else transaction.categoryId,
            notes = if (editedNotes != null) editedNotes.trim().takeIf { it.isNotBlank() } else transaction.notes,
        )
        return RowEditResult.Done(
            copy(
                transaction = nextTransaction,
                categoryPreview = if (categoryOverride != null) categoryOverride.preview else categoryPreview,
                edited = true,
            ),
        )
    }

    private fun detectedColumns(header: List<String>, columns: StatementCsvColumns): CsvImportDetectedColumns =
        CsvImportDetectedColumns(
            date = headerName(header, columns.dateIdx),
            merchant = headerName(header, columns.merchantIdx),
            amount = columns.amountIdx?.let { headerName(header, it) },
            debit = columns.debitIdx?.let { headerName(header, it) },
            credit = columns.creditIdx?.let { headerName(header, it) },
            currency = columns.currencyIdx?.let { headerName(header, it) },
            category = columns.categoryIdx?.let { headerName(header, it) },
            type = columns.typeIdx?.let { headerName(header, it) },
            notes = columns.notesIdx?.let { headerName(header, it) },
        )

    private fun headerName(header: List<String>, index: Int): String =
        header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

    private suspend fun existingImportIdentities(accountId: String): ExistingImportIdentities =
        ExistingImportIdentities(
            transactions.observeAll()
                .first()
                .filter { it.accountId == accountId && it.source == IngestSource.IMPORT },
        )

    private fun addImportableOrDuplicateSkip(
        rowNumber: Int,
        transaction: Transaction,
        identity: ImportIdentity,
        categoryPreview: String?,
        edited: Boolean,
        decisions: Map<Int, Boolean>,
        existingImports: ExistingImportIdentities,
        transactions: MutableList<CsvPlanTransaction>,
        previewRows: MutableList<CsvPlanTransaction>,
        skippedRows: MutableList<CsvImportSkippedRow>,
    ) {
        if (decisions[rowNumber] == false) {
            previewRows += CsvPlanTransaction(
                rowNumber = rowNumber,
                transaction = transaction,
                categoryPreview = categoryPreview,
                included = false,
                edited = edited,
            )
            skippedRows += CsvImportSkippedRow(
                rowNumber = rowNumber,
                reason = "Excluded from import",
            )
            return
        }
        if (existingImports.markSeenOrDuplicate(identity)) {
            skippedRows += CsvImportSkippedRow(
                rowNumber = rowNumber,
                reason = "Already imported",
            )
            return
        }
        transactions += CsvPlanTransaction(
            rowNumber = rowNumber,
            transaction = transaction,
            categoryPreview = categoryPreview,
            included = true,
            edited = edited,
        )
        previewRows += transactions.last()
    }

    private fun Transaction.withStableSourceRef(sourceRefId: String): Transaction =
        copy(sourceRefId = sourceRefId)

    private fun detectCsvFormat(headerLine: String, mapping: CsvImportColumnMapping?): CsvFormat? =
        CsvDelimiters
            .mapNotNull { delimiter ->
                val header = parseRow(headerLine, delimiter)
                val columns = StatementCsvMapper.detect(header, mapping) ?: return@mapNotNull null
                CsvFormat(
                    delimiter = delimiter,
                    header = header,
                    columns = columns,
                )
            }
            .maxByOrNull { it.header.size }

    private fun detectDelimitedHeader(headerLine: String): List<String> =
        CsvDelimiters
            .map { delimiter -> parseRow(headerLine, delimiter) }
            .maxByOrNull { it.size } ?: listOf(headerLine)

    /** RFC 4180-ish delimited row parser. Handles quoted delimiters, `""` escapes, trailing empties. */
    private fun parseRow(line: String, delimiter: Char = ','): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i += 1
        }
        out.add(cur.toString())
        return out
    }

    private sealed interface CsvPlanResult {
        data class Done(val plan: CsvImportPlan) : CsvPlanResult
        data class MappingRequired(val columns: List<String>, val reason: String) : CsvPlanResult
        data class Failed(val reason: String) : CsvPlanResult
    }

    private data class CsvImportPlan(
        val columns: CsvImportDetectedColumns,
        val availableColumns: List<String>,
        val transactions: List<CsvPlanTransaction>,
        val previewRows: List<CsvPlanTransaction>,
        val skippedRows: List<CsvImportSkippedRow>,
    )

    private data class CsvFormat(
        val delimiter: Char,
        val header: List<String>,
        val columns: StatementCsvColumns,
    )

    private data class CsvPlanTransaction(
        val rowNumber: Int,
        val transaction: Transaction,
        val categoryPreview: String?,
        val included: Boolean,
        val edited: Boolean,
    )

    private data class CsvMappedTransaction(
        val transaction: Transaction,
        val categoryPreview: String?,
        val edited: Boolean,
    )

    private sealed interface RowEditResult {
        data class Done(val row: CsvMappedTransaction) : RowEditResult
        data class Invalid(val reason: String) : RowEditResult
    }

    private companion object {
        const val PREVIEW_ROW_LIMIT = 5
        val CsvDelimiters = listOf(',', ';', '\t')
        val ISO_CURRENCY_REGEX = Regex("""[A-Z]{3}""")
    }
}

private fun List<CsvImportRowDecision>.shouldImportByRowNumber(): Map<Int, Boolean> =
    filter { it.rowNumber > 0 }
        .associate { it.rowNumber to it.shouldImport }

private fun List<CsvImportRowEdit>.byRowNumber(): Map<Int, CsvImportRowEdit> =
    filter { it.rowNumber > 0 }
        .associateBy { it.rowNumber }

private data class CategoryLookup(
    val byId: Map<String, Category>,
    val byName: Map<String, Category>,
    val byAr: Map<String, Category>,
) {
    fun categoryIdFor(raw: String): String? = resolve(raw)?.id

    fun resolve(raw: String): CategoryOverride? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return CategoryOverride(id = null, preview = null)

        val category = byId[trimmed.lowercase()]
            ?: byName[trimmed.lowercase()]
            ?: byAr[trimmed]
            ?: return null
        return CategoryOverride(id = category.id, preview = category.name)
    }
}

private data class CategoryOverride(
    val id: String?,
    val preview: String?,
)

private data class ImportIdentity(
    val sourceRefId: String,
    val sourceKey: String?,
    val contentKey: String,
)

private class ExistingImportIdentities(rows: List<Transaction>) {
    private val sourceRefs = rows.mapNotNull { it.sourceRefId }.toMutableSet()
    private val sourceKeys = rows.mapNotNull { it.sourceRefId?.let(::importSourceKey) }.toMutableSet()
    private val contentRemaining = rows
        .groupingBy(::statementContentKey)
        .eachCount()
        .toMutableMap()

    fun markSeenOrDuplicate(identity: ImportIdentity): Boolean {
        if (identity.sourceRefId in sourceRefs) return true
        if (identity.sourceKey != null && identity.sourceKey in sourceKeys) return true

        val remaining = contentRemaining[identity.contentKey] ?: 0
        if (remaining > 0) {
            if (remaining == 1) {
                contentRemaining -= identity.contentKey
            } else {
                contentRemaining[identity.contentKey] = remaining - 1
            }
            return true
        }

        sourceRefs += identity.sourceRefId
        identity.sourceKey?.let { sourceKeys += it }
        return false
    }
}

private class StableImportRefBuilder(
    private val accountId: String,
    private val format: String,
) {
    private val contentOccurrences = mutableMapOf<String, Int>()

    fun nextForExternalRef(raw: String, transaction: Transaction): ImportIdentity {
        val sourceKey = importSourceKey(raw)
        return ImportIdentity(
            sourceRefId = buildRef(sourceKey),
            sourceKey = sourceKey,
            contentKey = statementContentKey(transaction),
        )
    }

    fun nextForContent(transaction: Transaction): ImportIdentity {
        val content = statementContentKey(transaction)
        val occurrence = (contentOccurrences[content] ?: 0) + 1
        contentOccurrences[content] = occurrence
        return ImportIdentity(
            sourceRefId = buildRef("content=$content|occurrence=$occurrence"),
            sourceKey = null,
            contentKey = content,
        )
    }

    private fun buildRef(material: String): String =
        "import:$format:${sha256("account=${accountId.trim()}|$material").take(32)}"
}

private fun importSourceKey(raw: String): String =
    "source=${raw.trim().lowercase()}"

private fun statementContentKey(transaction: Transaction): String =
    listOf(
        "date=${transaction.date}",
        "merchant=${transaction.merchantNormalized.lowercase().trim()}",
        "amount=${normalizeAmount(transaction.amount.amount)}",
        "currency=${transaction.amount.currency.uppercase().trim()}",
        "type=${transaction.type.name}",
        "notes=${transaction.notes.orEmpty().trim()}",
    ).joinToString("|").let(::sha256)

private fun normalizeAmount(amount: BigDecimal): String =
    amount.stripTrailingZeros().toPlainString()

private fun sha256(raw: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}
