package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.repo.InvestmentImportPreview
import com.athar.core.domain.repo.InvestmentImportPreviewResult
import com.athar.core.domain.repo.InvestmentImportPreviewRow
import com.athar.core.domain.repo.InvestmentImportResult
import com.athar.core.domain.repo.InvestmentImportSkippedRow
import com.athar.core.domain.repo.InvestmentImportTrigger
import com.athar.core.domain.repo.InvestmentRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class InvestmentImporter @Inject constructor(
    private val investments: InvestmentRepository,
) : InvestmentImportTrigger {

    override suspend fun preview(input: InputStream): InvestmentImportPreviewResult {
        return when (val plan = buildPlan(input)) {
            is InvestmentPlanResult.Done -> InvestmentImportPreviewResult.Done(plan.plan.toPreview())
            is InvestmentPlanResult.Failed -> InvestmentImportPreviewResult.Failed(plan.reason)
        }
    }

    override suspend fun import(input: InputStream): InvestmentImportResult {
        val plan = when (val result = buildPlan(input)) {
            is InvestmentPlanResult.Done -> result.plan
            is InvestmentPlanResult.Failed -> return InvestmentImportResult.Failed(result.reason)
        }

        investments.upsertPool(plan.pool)
        plan.existingContributions.forEach { investments.deleteContribution(it.id) }
        plan.contributions.forEach { investments.upsertContribution(it) }

        Timber.i(
            "Investment import: pool=%s imported=%d replaced=%d skipped=%d existing=%s",
            plan.pool.name,
            plan.contributions.size,
            plan.existingContributions.size,
            plan.skippedRows.size,
            plan.existingPool,
        )
        return InvestmentImportResult.Done(
            importedContributions = plan.contributions.size,
            replacedContributions = plan.existingContributions.size,
            skipped = plan.skippedRows.size,
            existingPool = plan.existingPool,
        )
    }

    private suspend fun buildPlan(input: InputStream): InvestmentPlanResult {
        val bytes = runCatching { input.use { it.readBytes() } }
            .getOrElse { return InvestmentPlanResult.Failed("Couldn't read workbook: ${it.message}") }
        if (!OpenXmlWorkbookReader.looksLikeXlsx(bytes)) {
            return InvestmentPlanResult.Failed("Select a TMOAP .xlsx workbook to import family investments.")
        }

        val workbook = runCatching { OpenXmlWorkbookReader.read(bytes) }
            .getOrElse { return InvestmentPlanResult.Failed("Couldn't read XLSX workbook: ${it.message}") }
        val selected = workbook.sheets
            .firstNotNullOfOrNull { sheet ->
                workbook.rows(sheet)
                    ?.takeIf { isInvestmentSheetName(sheet.name) && findHeader(it) != null }
                    ?.let { sheet to it }
            }
            ?: workbook.sheets.firstNotNullOfOrNull { sheet ->
                workbook.rows(sheet)
                    ?.takeIf { findHeader(it) != null }
                    ?.let { sheet to it }
            }
            ?: return InvestmentPlanResult.Failed("This workbook does not include a Family Investments sheet.")

        val sheet = selected.first
        val grid = selected.second
        val header = findHeader(grid)
            ?: return InvestmentPlanResult.Failed("Family Investments sheet does not include Amount and Owner columns.")

        val existing = investments.observePoolsWithContributions().first()
        val existingPool = existing.keys.firstOrNull { poolNamesMatch(it.name, sheet.name) }
        val existingContributions = existingPool?.let { existing[it].orEmpty() }.orEmpty()
        val currency = existingPool?.totalReturn?.currency ?: Money.SAR
        val poolId = existingPool?.id ?: UUID.randomUUID().toString()

        val rows = mutableListOf<InvestmentPlanRow>()
        val skipped = mutableListOf<InvestmentImportSkippedRow>()
        var firstReturn: BigDecimal? = null
        var fallbackReturn: BigDecimal? = null
        var period: String? = null
        var sawTableRow = false

        for (index in (header.rowIndex + 1) until grid.size) {
            val rawRow = grid[index]
            val rowNumber = index + 1
            val owner = rawRow.getOrNull(header.ownerColumn)?.trim().orEmpty()
            val amountRaw = rawRow.getOrNull(header.amountColumn)?.trim().orEmpty()
            val returnRaw = header.returnColumn?.let { rawRow.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val periodRaw = header.periodColumn?.let { rawRow.getOrNull(it)?.trim().orEmpty() }.orEmpty()

            val hasTableValue = owner.isNotBlank() || amountRaw.isNotBlank()
            if (!hasTableValue) {
                if (sawTableRow) break
                continue
            }
            sawTableRow = true
            if (isTotalLabel(owner)) break

            val parsedReturn = returnRaw.takeIf { it.isNotBlank() }?.let(::parseAmount)
            if (parsedReturn != null) {
                if (!isTotalLabel(periodRaw) && firstReturn == null) firstReturn = parsedReturn
                if (fallbackReturn == null) fallbackReturn = parsedReturn
            }
            if (period == null && periodRaw.isNotBlank() && !isTotalLabel(periodRaw)) {
                period = periodRaw
            }

            if (owner.isBlank()) {
                skipped += InvestmentImportSkippedRow(
                    rowNumber = rowNumber,
                    label = "Row $rowNumber",
                    reason = "Owner is blank.",
                )
                continue
            }
            val amount = parseAmount(amountRaw)
            if (amount == null || amount.signum() <= 0) {
                skipped += InvestmentImportSkippedRow(
                    rowNumber = rowNumber,
                    label = owner,
                    reason = "Contribution amount is not a positive amount.",
                )
                continue
            }

            rows += InvestmentPlanRow(
                rowNumber = rowNumber,
                contribution = InvestmentContribution(
                    id = UUID.randomUUID().toString(),
                    poolId = poolId,
                    ownerName = owner,
                    amount = Money.of(amount, currency).rounded(),
                ),
            )
        }

        if (rows.isEmpty() && skipped.isEmpty()) {
            return InvestmentPlanResult.Failed("Family Investments sheet does not include contribution rows.")
        }
        if (rows.isEmpty()) {
            return InvestmentPlanResult.Failed("Family Investments sheet did not include valid contribution rows.")
        }

        val totalReturn = Money.of(firstReturn ?: fallbackReturn ?: BigDecimal.ZERO, currency).rounded()
        val pool = InvestmentPool(
            id = poolId,
            name = sheet.name.takeIf { it.isNotBlank() } ?: DEFAULT_POOL_NAME,
            period = period ?: DEFAULT_PERIOD,
            totalReturn = totalReturn,
        )

        return InvestmentPlanResult.Done(
            InvestmentPlan(
                pool = pool,
                contributions = rows.map { it.contribution },
                rowNumbers = rows.associate { it.contribution.id to it.rowNumber },
                existingPool = existingPool != null,
                existingContributions = existingContributions,
                skippedRows = skipped,
            ),
        )
    }

    private fun InvestmentPlan.toPreview(): InvestmentImportPreview {
        val currency = pool.totalReturn.currency
        return InvestmentImportPreview(
            poolName = pool.name,
            period = pool.period,
            existingPool = existingPool,
            contributionRows = contributions.size,
            replacedContributions = existingContributions.size,
            skipped = skippedRows.size,
            totalCorpus = Money.sumAmounts(contributions.map { it.amount }, intoCurrency = currency).rounded(),
            totalReturn = pool.totalReturn,
            sampleRows = contributions.take(PREVIEW_ROW_LIMIT).map { contribution ->
                InvestmentImportPreviewRow(
                    rowNumber = rowNumbers.getValue(contribution.id),
                    ownerName = contribution.ownerName,
                    amount = contribution.amount,
                )
            },
            skippedRows = skippedRows.take(PREVIEW_ROW_LIMIT),
        )
    }

    private fun findHeader(grid: List<List<String>>): InvestmentHeader? {
        grid.forEachIndexed { rowIndex, row ->
            val amountColumn = row.indexOfFirst(::isAmountHeader)
            val ownerColumn = row.indexOfFirst(::isOwnerHeader)
            if (amountColumn >= 0 && ownerColumn >= 0) {
                return InvestmentHeader(
                    rowIndex = rowIndex,
                    amountColumn = amountColumn,
                    ownerColumn = ownerColumn,
                    returnColumn = row.indexOfFirst(::isReturnHeader).takeIf { it >= 0 },
                    periodColumn = row.indexOfFirst(::isPeriodHeader).takeIf { it >= 0 },
                )
            }
        }
        return null
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val normalizedDigits = normalizeDigits(raw).trim()
        if (normalizedDigits.isBlank()) return null

        val normalizedSeparators = normalizedDigits
            .replace('٫', '.')
            .replace('٬', ',')
            .replace('\u00A0', ' ')
        val negative =
            normalizedSeparators.startsWith("-") ||
                normalizedSeparators.endsWith("-") ||
                normalizedSeparators.contains("(") && normalizedSeparators.contains(")")
        val numeric = normalizedSeparators
            .replace(Regex("""[A-Za-z\u0600-\u06FF.]*ر\.س[A-Za-z\u0600-\u06FF.]*"""), "")
            .replace("SAR", "", ignoreCase = true)
            .replace(Regex("""[^\d,.\-+]"""), "")
            .replace("+", "")
            .replace("-", "")
            .trim()
        if (numeric.isBlank()) return null

        val canonical = canonicalDecimal(numeric) ?: return null
        val value = runCatching { BigDecimal(canonical) }.getOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    private fun canonicalDecimal(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')

        return when {
            lastComma >= 0 && lastDot >= 0 -> {
                val decimal = if (lastComma > lastDot) ',' else '.'
                s.filter { it.isDigit() || it == decimal }
                    .replace(decimal, '.')
            }
            lastComma >= 0 -> normalizeSingleSeparator(s, ',')
            lastDot >= 0 -> normalizeSingleSeparator(s, '.')
            else -> s.filter { it.isDigit() }
        }.takeIf { it.isNotBlank() }
    }

    private fun normalizeSingleSeparator(raw: String, separator: Char): String {
        val parts = raw.split(separator)
        return if (parts.size == 2 && parts[1].length in 1..2) {
            parts[0].filter(Char::isDigit) + "." + parts[1].filter(Char::isDigit)
        } else {
            raw.filter(Char::isDigit)
        }
    }

    private fun isInvestmentSheetName(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized.contains("investment") || raw.contains("استثمارات")
    }

    private fun poolNamesMatch(existing: String, imported: String): Boolean =
        normalizeName(existing) == normalizeName(imported) ||
            isFamilyInvestmentName(existing) && isFamilyInvestmentName(imported)

    private fun isFamilyInvestmentName(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized.contains("family investment") || raw.contains("استثمارات العائلة")
    }

    private fun isAmountHeader(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized == "amount" || normalized == "contribution amount" || raw.contains("المبلغ")
    }

    private fun isOwnerHeader(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized == "owner" || normalized == "owner name" || raw.contains("المالك")
    }

    private fun isReturnHeader(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized.contains("return") || raw.contains("العائد")
    }

    private fun isPeriodHeader(raw: String): Boolean {
        val normalized = normalizeHeader(raw)
        return normalized == "period" || raw.contains("الفترة")
    }

    private fun isTotalLabel(raw: String): Boolean {
        val normalized = normalizeName(raw)
        return normalized == "total" || normalized.contains("المجموع")
    }

    private fun normalizeHeader(raw: String): String =
        raw.lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun normalizeName(raw: String): String =
        raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("""\s+"""), " ")

    private fun normalizeDigits(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                },
            )
        }
    }

    private sealed interface InvestmentPlanResult {
        data class Done(val plan: InvestmentPlan) : InvestmentPlanResult
        data class Failed(val reason: String) : InvestmentPlanResult
    }

    private data class InvestmentPlan(
        val pool: InvestmentPool,
        val contributions: List<InvestmentContribution>,
        val rowNumbers: Map<String, Int>,
        val existingPool: Boolean,
        val existingContributions: List<InvestmentContribution>,
        val skippedRows: List<InvestmentImportSkippedRow>,
    )

    private data class InvestmentPlanRow(
        val rowNumber: Int,
        val contribution: InvestmentContribution,
    )

    private data class InvestmentHeader(
        val rowIndex: Int,
        val amountColumn: Int,
        val ownerColumn: Int,
        val returnColumn: Int?,
        val periodColumn: Int?,
    )

    private companion object {
        const val DEFAULT_POOL_NAME = "Family Investments"
        const val DEFAULT_PERIOD = "Imported"
        const val PREVIEW_ROW_LIMIT = 6
    }
}
