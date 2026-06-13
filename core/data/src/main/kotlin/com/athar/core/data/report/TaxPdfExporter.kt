package com.athar.core.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.athar.core.common.time.Period
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TaxExportResult
import com.athar.core.domain.repo.TaxExportTrigger
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.OutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate as JavaLocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TaxPdfExporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : TaxExportTrigger {

    override suspend fun exportAnnual(
        output: OutputStream,
        year: Int,
        localeTag: String,
    ): TaxExportResult {
        if (year !in MIN_YEAR..MAX_YEAR) {
            return TaxExportResult.Failed("Year must be between $MIN_YEAR and $MAX_YEAR.")
        }

        return runCatching {
            val period = Period.Year(year)
            val txs = transactions.observeByPeriod(period, TxStatus.CONFIRMED).first()
            val cats = categories.observeAll(kind = null, includeArchived = true).first()
            val report = TaxReportBuilder.build(
                year = year,
                transactions = txs,
                categories = cats,
                localeTag = localeTag,
            )
            TaxReportPdfRenderer(localeTag).render(report, output)
            report
        }.fold(
            onSuccess = { report ->
                Timber.i(
                    "Tax PDF export: year=%d tx=%d totals=%d excludedReconciliations=%d",
                    year,
                    report.transactions.size,
                    report.categoryTotals.size,
                    report.excludedReconciliationCount,
                )
                TaxExportResult.Done(
                    year = year,
                    transactions = report.transactions.size,
                    categoryTotals = report.categoryTotals.size,
                    excludedReconciliations = report.excludedReconciliationCount,
                )
            },
            onFailure = {
                Timber.e(it, "Tax PDF export failed")
                TaxExportResult.Failed(it.message ?: "unknown error")
            },
        )
    }

    private companion object {
        const val MIN_YEAR = 2000
        const val MAX_YEAR = 2100
    }
}

private class TaxReportPdfRenderer(localeTag: String) {
    private val reportLocale = if (localeTag.lowercase().startsWith("ar")) TaxReportLocale.AR else TaxReportLocale.EN
    private val javaLocale = if (reportLocale == TaxReportLocale.AR) Locale("ar") else Locale.ENGLISH
    private val strings = TaxPdfStrings.forLocale(reportLocale)
    private val numberFormat = NumberFormat.getNumberInstance(javaLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val isRtl = reportLocale == TaxReportLocale.AR
    private val document = PdfDocument()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 29, 24)
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val titlePaint = Paint(bodyPaint).apply {
        textSize = 18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val headingPaint = Paint(bodyPaint).apply {
        textSize = 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val mutedPaint = Paint(bodyPaint).apply {
        color = Color.rgb(104, 96, 82)
    }
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = TOP_MARGIN

    fun render(report: TaxReport, output: OutputStream) {
        try {
            startPage()
            drawTitle(strings.title(report.year))
            drawLine(strings.generated(JavaLocalDate.now().toString()), mutedPaint)
            drawSpacer()

            drawHeading(strings.summary)
            drawLine(strings.transactionCount(report.transactions.size))
            drawLine(strings.categoryTotalCount(report.categoryTotals.size))
            if (report.excludedReconciliationCount > 0) {
                drawLine(strings.excludedReconciliations(report.excludedReconciliationCount), mutedPaint)
            }
            drawSpacer()

            drawHeading(strings.typeTotals)
            val totalsByType = report.transactions
                .groupBy { it.type to it.currency }
                .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount } }
                .toList()
                .sortedWith(compareBy<Pair<Pair<TxType, String>, BigDecimal>> { it.first.first.sortOrder() }.thenBy { it.first.second })
            if (totalsByType.isEmpty()) {
                drawLine(strings.noTransactions, mutedPaint)
            } else {
                totalsByType.forEach { (key, amount) ->
                    val (type, currency) = key
                    drawLine("${strings.typeLabel(type)}: ${formatAmount(amount, currency)}")
                }
            }
            drawSpacer()

            drawHeading(strings.categoryTotals)
            if (report.categoryTotals.isEmpty()) {
                drawLine(strings.noTransactions, mutedPaint)
            } else {
                report.categoryTotals.forEach { total ->
                    drawLine(
                        "${strings.typeLabel(total.type)} | ${total.categoryName} | " +
                            "${formatAmount(total.amount, total.currency)} | ${strings.rowCount(total.count)}",
                    )
                }
            }
            drawSpacer()

            drawHeading(strings.transactionList)
            if (report.transactions.isEmpty()) {
                drawLine(strings.noTransactions, mutedPaint)
            } else {
                report.transactions.forEach { row ->
                    drawLine(
                        "${row.date} | ${strings.typeLabel(row.type)} | ${row.categoryName} | " +
                            "${row.merchant} | ${formatAmount(row.amount, row.currency)}",
                    )
                    row.notes?.let { notes ->
                        drawLine("${strings.notes}: $notes", mutedPaint, indent = 14f)
                    }
                }
            }

            finishPage()
            output.use { document.writeTo(it) }
        } finally {
            page?.let {
                document.finishPage(it)
                page = null
            }
            document.close()
        }
    }

    private fun startPage() {
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        canvas = page!!.canvas
        canvas.drawColor(Color.WHITE)
        y = TOP_MARGIN
        drawFooter()
    }

    private fun finishPage() {
        page?.let { document.finishPage(it) }
        page = null
    }

    private fun ensureSpace(required: Float = LINE_HEIGHT) {
        if (y + required <= PAGE_HEIGHT - BOTTOM_MARGIN) return
        finishPage()
        startPage()
    }

    private fun drawTitle(text: String) {
        ensureSpace(28f)
        drawWrapped(text, titlePaint, 22f)
    }

    private fun drawHeading(text: String) {
        ensureSpace(24f)
        drawWrapped(text, headingPaint, 18f)
    }

    private fun drawLine(text: String, paint: Paint = bodyPaint, indent: Float = 0f) {
        drawWrapped(text, paint, LINE_HEIGHT, indent)
    }

    private fun drawSpacer() {
        ensureSpace(10f)
        y += 8f
    }

    private fun drawWrapped(text: String, paint: Paint, lineHeight: Float, indent: Float = 0f) {
        val availableWidth = PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN - indent
        wrap(text, paint, availableWidth).forEach { line ->
            ensureSpace(lineHeight)
            val x = if (isRtl) PAGE_WIDTH - RIGHT_MARGIN - indent else LEFT_MARGIN + indent
            paint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
    }

    private fun drawFooter() {
        mutedPaint.textAlign = Paint.Align.CENTER
        mutedPaint.textSize = 9f
        canvas.drawText("Athar", PAGE_WIDTH / 2f, PAGE_HEIGHT - 22f, mutedPaint)
        mutedPaint.textSize = 10f
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        if (paint.measureText(text) <= width) return listOf(text)
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf(text)

        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val next = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(next) <= width) {
                current = next
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun formatAmount(amount: BigDecimal, currency: String): String =
        "${numberFormat.format(amount)} $currency"

    private fun TxType.sortOrder(): Int =
        when (this) {
            TxType.INCOME -> 0
            TxType.EXPENSE -> 1
            TxType.TRANSFER -> 2
        }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val LEFT_MARGIN = 48f
        const val RIGHT_MARGIN = 48f
        const val TOP_MARGIN = 54f
        const val BOTTOM_MARGIN = 54f
        const val LINE_HEIGHT = 14f
    }
}

private data class TaxPdfStrings(
    val summary: String,
    val typeTotals: String,
    val categoryTotals: String,
    val transactionList: String,
    val notes: String,
    val noTransactions: String,
    val title: (Int) -> String,
    val generated: (String) -> String,
    val transactionCount: (Int) -> String,
    val categoryTotalCount: (Int) -> String,
    val excludedReconciliations: (Int) -> String,
    val rowCount: (Int) -> String,
    val typeLabel: (TxType) -> String,
) {
    companion object {
        fun forLocale(locale: TaxReportLocale): TaxPdfStrings =
            when (locale) {
                TaxReportLocale.AR -> TaxPdfStrings(
                    summary = "الملخص",
                    typeTotals = "الإجمالي حسب النوع",
                    categoryTotals = "الإجمالي حسب الفئة",
                    transactionList = "قائمة الحركات",
                    notes = "ملاحظات",
                    noTransactions = "لا توجد حركات مؤكدة لهذه السنة.",
                    title = { year -> "تقرير الضرائب والمحاسب $year" },
                    generated = { date -> "تاريخ الإنشاء: $date" },
                    transactionCount = { count -> "الحركات التشغيلية: $count" },
                    categoryTotalCount = { count -> "إجماليات الفئات: $count" },
                    excludedReconciliations = { count -> "تسويات الرصيد المستبعدة: $count" },
                    rowCount = { count -> "$count حركة" },
                    typeLabel = { type ->
                        when (type) {
                            TxType.INCOME -> "دخل"
                            TxType.EXPENSE -> "مصروف"
                            TxType.TRANSFER -> "تحويل"
                        }
                    },
                )
                TaxReportLocale.EN -> TaxPdfStrings(
                    summary = "Summary",
                    typeTotals = "Totals by type",
                    categoryTotals = "Totals by category",
                    transactionList = "Transaction list",
                    notes = "Notes",
                    noTransactions = "No confirmed operating transactions for this year.",
                    title = { year -> "Tax and accountant report $year" },
                    generated = { date -> "Generated: $date" },
                    transactionCount = { count -> "Operating transactions: $count" },
                    categoryTotalCount = { count -> "Category totals: $count" },
                    excludedReconciliations = { count -> "Excluded reconciliation adjustments: $count" },
                    rowCount = { count -> "$count tx" },
                    typeLabel = { type ->
                        when (type) {
                            TxType.INCOME -> "Income"
                            TxType.EXPENSE -> "Expense"
                            TxType.TRANSFER -> "Transfer"
                        }
                    },
                )
            }
    }
}
