package com.athar.core.data.csv

import com.athar.core.common.time.Period
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CsvExportResult
import com.athar.core.domain.repo.CsvExportTrigger
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import timber.log.Timber
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CsvExporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : CsvExportTrigger {

    override suspend fun exportAll(output: OutputStream): CsvExportResult = runCatching {
        // 20-year lookback covers anything realistic; we don't bother with a date filter UI.
        val window = Period.Custom(start = LocalDate(2005, 1, 1), endExclusive = LocalDate(2100, 1, 1))
        val all = transactions.observeByPeriod(window, status = TxStatus.CONFIRMED).first()
        val byId = categories.observeAll(includeArchived = true).first().associateBy { it.id }

        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write("date,vendor,amount,currency,category,type,notes\n")
            all.forEach { tx ->
                val catName = tx.categoryId?.let { byId[it]?.name } ?: ""
                writer.write(
                    listOf(
                        tx.date.toString(),
                        tx.merchant,
                        tx.amount.amount.toPlainString(),
                        tx.amount.currency,
                        catName,
                        tx.type.name,
                        tx.notes.orEmpty(),
                    ).joinToString(",") { csvEscape(it) },
                )
                writer.write("\n")
            }
            writer.flush()
        }
        all.size
    }.fold(
        onSuccess = {
            Timber.i("CSV export: %d transactions written", it)
            CsvExportResult.Done(exported = it)
        },
        onFailure = {
            Timber.e(it, "CSV export failed")
            CsvExportResult.Failed(reason = it.message ?: "unknown error")
        },
    )

    /** RFC-4180 escape: wrap in quotes if field contains comma/quote/newline; double internal quotes. */
    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuoting = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }
}
