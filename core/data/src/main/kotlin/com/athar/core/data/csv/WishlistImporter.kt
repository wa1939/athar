package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.repo.WishlistImportPreview
import com.athar.core.domain.repo.WishlistImportPreviewResult
import com.athar.core.domain.repo.WishlistImportPreviewRow
import com.athar.core.domain.repo.WishlistImportResult
import com.athar.core.domain.repo.WishlistImportSkippedRow
import com.athar.core.domain.repo.WishlistImportTrigger
import com.athar.core.domain.repo.WishlistRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WishlistImporter @Inject constructor(
    private val wishlist: WishlistRepository,
    private val clock: Clock,
) : WishlistImportTrigger {

    override suspend fun preview(input: InputStream): WishlistImportPreviewResult {
        return when (val plan = buildPlan(input)) {
            is WishlistPlanResult.Done -> WishlistImportPreviewResult.Done(plan.plan.toPreview())
            is WishlistPlanResult.Failed -> WishlistImportPreviewResult.Failed(plan.reason)
        }
    }

    override suspend fun import(input: InputStream): WishlistImportResult {
        val plan = when (val result = buildPlan(input)) {
            is WishlistPlanResult.Done -> result.plan
            is WishlistPlanResult.Failed -> return WishlistImportResult.Failed(result.reason)
        }

        plan.rows.forEach { row -> wishlist.upsert(row.item) }

        Timber.i(
            "Wishlist import: imported=%d new=%d updated=%d skipped=%d",
            plan.rows.size,
            plan.newCount,
            plan.updatedCount,
            plan.skippedRows.size,
        )
        return WishlistImportResult.Done(
            imported = plan.rows.size,
            newItems = plan.newCount,
            updatedItems = plan.updatedCount,
            skipped = plan.skippedRows.size,
        )
    }

    private suspend fun buildPlan(input: InputStream): WishlistPlanResult {
        val bytes = runCatching { input.use { it.readBytes() } }
            .getOrElse { return WishlistPlanResult.Failed("Couldn't read workbook: ${it.message}") }
        if (!OpenXmlWorkbookReader.looksLikeXlsx(bytes)) {
            return WishlistPlanResult.Failed("Select a TMOAP .xlsx workbook to import wishlist items.")
        }

        val workbook = runCatching { OpenXmlWorkbookReader.read(bytes) }
            .getOrElse { return WishlistPlanResult.Failed("Couldn't read XLSX workbook: ${it.message}") }
        val sheet = workbook.sheets.firstOrNull { it.name.equals(WISHLIST_SHEET, ignoreCase = true) }
            ?: return WishlistPlanResult.Failed("This workbook does not include a Wishlist sheet.")
        val grid = workbook.rows(sheet).orEmpty()
        if (grid.isEmpty()) {
            return WishlistPlanResult.Failed("Wishlist sheet is empty.")
        }

        val header = findHeader(grid)
            ?: return WishlistPlanResult.Failed("Wishlist sheet does not include the expected Item and Cost columns.")
        val existing = wishlist.observeAll().first()
        val existingByName = existing.associateBy { normalizeName(it.name) }
        val defaultStartMonth = currentMonth()
        val rows = mutableListOf<WishlistPlanRow>()
        val skipped = mutableListOf<WishlistImportSkippedRow>()

        grid.drop(header.rowIndex + 1).forEachIndexed { offset, rawRow ->
            val rowNumber = header.rowIndex + offset + 2
            if (rawRow.all { it.isBlank() }) return@forEachIndexed

            val name = rawRow.getOrNull(header.itemColumn)?.trim().orEmpty()
            if (name.isBlank()) {
                skipped += WishlistImportSkippedRow(
                    rowNumber = rowNumber,
                    label = "Row $rowNumber",
                    reason = "Item name is blank.",
                )
                return@forEachIndexed
            }

            val costAmount = parseAmount(rawRow.getOrNull(header.costColumn).orEmpty())
            if (costAmount == null || costAmount.signum() <= 0) {
                skipped += WishlistImportSkippedRow(
                    rowNumber = rowNumber,
                    label = name,
                    reason = "Cost is not a positive amount.",
                )
                return@forEachIndexed
            }

            val savedRaw = header.currentSavedColumn?.let { rawRow.getOrNull(it) }.orEmpty()
            val savedAmount = if (savedRaw.isBlank()) BigDecimal.ZERO else parseAmount(savedRaw)
            if (savedAmount == null || savedAmount.signum() < 0) {
                skipped += WishlistImportSkippedRow(
                    rowNumber = rowNumber,
                    label = name,
                    reason = "Current saved is not a non-negative amount.",
                )
                return@forEachIndexed
            }

            val desiredMonths = header.desiredMonthsColumn
                ?.let { rawRow.getOrNull(it).orEmpty() }
                ?.takeIf { it.isNotBlank() }
                ?.let(::parsePositiveInt)
            if (
                header.desiredMonthsColumn != null &&
                rawRow.getOrNull(header.desiredMonthsColumn).orEmpty().isNotBlank() &&
                desiredMonths == null
            ) {
                skipped += WishlistImportSkippedRow(
                    rowNumber = rowNumber,
                    label = name,
                    reason = "Desired months is not a positive whole number.",
                )
                return@forEachIndexed
            }

            val startMonth = header.startMonthColumn
                ?.let { rawRow.getOrNull(it).orEmpty() }
                ?.takeIf { it.isNotBlank() }
                ?.let(::parseYearMonth)
                ?: defaultStartMonth
            if (
                header.startMonthColumn != null &&
                rawRow.getOrNull(header.startMonthColumn).orEmpty().isNotBlank() &&
                parseYearMonth(rawRow.getOrNull(header.startMonthColumn).orEmpty()) == null
            ) {
                skipped += WishlistImportSkippedRow(
                    rowNumber = rowNumber,
                    label = name,
                    reason = "Start month is not a readable month.",
                )
                return@forEachIndexed
            }

            val matched = existingByName[normalizeName(name)]
            val currency = matched?.cost?.currency ?: Money.SAR
            val notes = header.notesColumn
                ?.let { rawRow.getOrNull(it).orEmpty().trim() }
                ?.takeIf { it.isNotBlank() }
                ?: matched?.notes

            rows += WishlistPlanRow(
                rowNumber = rowNumber,
                item = WishlistItem(
                    id = matched?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    cost = Money.of(costAmount, currency).rounded(),
                    currentSaved = Money.of(savedAmount, currency).rounded(),
                    desiredMonths = desiredMonths,
                    startMonth = startMonth,
                    notes = notes,
                ),
                existing = matched != null,
            )
        }

        if (rows.isEmpty() && skipped.isEmpty()) {
            return WishlistPlanResult.Failed("Wishlist sheet does not include item rows.")
        }

        return WishlistPlanResult.Done(
            WishlistPlan(
                rows = rows,
                skippedRows = skipped,
            ),
        )
    }

    private fun WishlistPlan.toPreview(): WishlistImportPreview {
        val currency = rows.firstOrNull()?.item?.cost?.currency ?: Money.SAR
        return WishlistImportPreview(
            itemRows = rows.size,
            newItems = newCount,
            updatedItems = updatedCount,
            skipped = skippedRows.size,
            totalCost = Money.sumAmounts(rows.map { it.item.cost }, intoCurrency = currency).rounded(),
            totalSaved = Money.sumAmounts(rows.map { it.item.currentSaved }, intoCurrency = currency).rounded(),
            sampleRows = rows.take(PREVIEW_ROW_LIMIT).map { row ->
                WishlistImportPreviewRow(
                    rowNumber = row.rowNumber,
                    name = row.item.name,
                    cost = row.item.cost,
                    currentSaved = row.item.currentSaved,
                    desiredMonths = row.item.desiredMonths,
                    startMonth = row.item.startMonth,
                    existing = row.existing,
                )
            },
            skippedRows = skippedRows.take(PREVIEW_ROW_LIMIT),
        )
    }

    private fun findHeader(grid: List<List<String>>): WishlistHeader? {
        grid.forEachIndexed { rowIndex, row ->
            val itemColumn = row.headerIndex("item")
            val costColumn = row.headerIndex("cost")
            if (itemColumn >= 0 && costColumn >= 0) {
                return WishlistHeader(
                    rowIndex = rowIndex,
                    itemColumn = itemColumn,
                    costColumn = costColumn,
                    currentSavedColumn = row.headerIndex("current saved").takeIf { it >= 0 },
                    desiredMonthsColumn = row.headerIndex("desired months").takeIf { it >= 0 },
                    startMonthColumn = row.headerIndex("start month").takeIf { it >= 0 },
                    notesColumn = row.headerIndex("notes").takeIf { it >= 0 },
                )
            }
        }
        return null
    }

    private fun List<String>.headerIndex(target: String): Int =
        indexOfFirst { cell ->
            val normalized = normalizeHeader(cell)
            normalized == target || normalized.startsWith("$target ")
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

    private fun parsePositiveInt(raw: String): Int? {
        val amount = parseAmount(raw) ?: return null
        if (amount.signum() <= 0) return null
        val whole = amount.stripTrailingZeros()
        if (whole.scale() > 0) return null
        return whole.toIntExactOrNull()
    }

    private fun BigDecimal.toIntExactOrNull(): Int? =
        runCatching { intValueExact() }.getOrNull()

    private fun parseYearMonth(raw: String): YearMonth? {
        val normalized = normalizeDigits(raw).trim()
        if (normalized.isBlank()) return null

        YearMonthPattern.find(normalized)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            return runCatching { YearMonth.of(year, month) }.getOrNull()
        }

        runCatching {
            val date = LocalDate.parse(normalized)
            YearMonth.of(date.year, date.monthValue)
        }.getOrNull()?.let { return it }

        if (ExcelSerialPattern.matches(normalized)) {
            parseAmount(normalized)
                ?.takeIf { it.signum() > 0 && it.stripTrailingZeros().scale() <= 0 }
                ?.let { serial ->
                    return runCatching {
                        val date = EXCEL_DATE_EPOCH.plusDays(serial.longValueExact())
                        YearMonth.of(date.year, date.monthValue)
                    }.getOrNull()
                }
        }
        return null
    }

    private fun currentMonth(): YearMonth {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
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

    private sealed interface WishlistPlanResult {
        data class Done(val plan: WishlistPlan) : WishlistPlanResult
        data class Failed(val reason: String) : WishlistPlanResult
    }

    private data class WishlistPlan(
        val rows: List<WishlistPlanRow>,
        val skippedRows: List<WishlistImportSkippedRow>,
    ) {
        val newCount: Int = rows.count { !it.existing }
        val updatedCount: Int = rows.count { it.existing }
    }

    private data class WishlistPlanRow(
        val rowNumber: Int,
        val item: WishlistItem,
        val existing: Boolean,
    )

    private data class WishlistHeader(
        val rowIndex: Int,
        val itemColumn: Int,
        val costColumn: Int,
        val currentSavedColumn: Int?,
        val desiredMonthsColumn: Int?,
        val startMonthColumn: Int?,
        val notesColumn: Int?,
    )

    private companion object {
        const val WISHLIST_SHEET = "Wishlist"
        const val PREVIEW_ROW_LIMIT = 6
        val EXCEL_DATE_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
        val ExcelSerialPattern: Regex = Regex("""\d+(?:\.0+)?""")
        val YearMonthPattern: Regex = Regex("""(\d{4})[-/](\d{1,2})(?:[-/]\d{1,2})?""")
    }
}
