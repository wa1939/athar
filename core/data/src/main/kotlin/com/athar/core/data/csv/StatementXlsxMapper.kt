package com.athar.core.data.csv

import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CsvImportColumnMapping
import com.athar.core.domain.repo.CsvImportDetectedColumns
import kotlinx.datetime.LocalDate
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal data class StatementXlsxMappedRow(
    val rowNumber: Int,
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TxType,
    val category: String?,
    val notes: String?,
)

internal sealed interface StatementXlsxParseResult {
    data class Done(
        val columns: CsvImportDetectedColumns,
        val availableColumns: List<String>,
        val rows: List<StatementXlsxMappedRow>,
        val skippedRowNumbers: List<Int>,
    ) : StatementXlsxParseResult

    data class MappingRequired(val columns: List<String>, val reason: String) : StatementXlsxParseResult
    data class Failed(val reason: String) : StatementXlsxParseResult
}

internal object StatementXlsxMapper {

    fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    fun parse(
        bytes: ByteArray,
        mapping: CsvImportColumnMapping? = null,
    ): StatementXlsxParseResult {
        val entries = runCatching { unzip(bytes) }
            .getOrElse { return StatementXlsxParseResult.Failed("Couldn't read XLSX workbook: ${it.message}") }
        if ("xl/workbook.xml" !in entries) {
            return StatementXlsxParseResult.Failed("XLSX workbook is missing xl/workbook.xml.")
        }

        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheets = parseWorkbookSheets(entries)
        if (sheets.isEmpty()) {
            return StatementXlsxParseResult.Failed("XLSX workbook did not include worksheets.")
        }

        var nextRowNumber = 1
        var firstDetectedColumns: CsvImportDetectedColumns? = null
        var firstAvailableColumns: List<String> = emptyList()
        var bestMappingHeader: List<String> = emptyList()
        val rows = mutableListOf<StatementXlsxMappedRow>()
        val skipped = mutableListOf<Int>()

        sheets.forEach { sheet ->
            val xml = entries[sheet.path] ?: return@forEach
            val grid = parseWorksheet(xml, sharedStrings)
            val candidateHeader = grid
                .map { it.trimTrailingBlankCells() }
                .filter { it.count(String::isNotBlank) >= 2 }
                .maxByOrNull { it.count(String::isNotBlank) }
            if (bestMappingHeader.isEmpty() && candidateHeader != null) {
                bestMappingHeader = candidateHeader
            }

            val detected = detectHeader(grid, mapping) ?: return@forEach
            val header = detected.header
            val columns = detected.columns
            if (firstDetectedColumns == null) {
                firstDetectedColumns = detectedColumns(header, columns, prefix = "XLSX")
                firstAvailableColumns = header
            }

            val fallbackType = sheet.positiveAmountFallbackType()
            grid.drop(detected.rowIndex + 1).forEach { rawRow ->
                val row = rawRow.trimTrailingBlankCells()
                if (row.all(String::isBlank)) return@forEach

                val rowNumber = nextRowNumber++
                val mapped = StatementCsvMapper.map(row, columns, positiveAmountFallbackType = fallbackType)
                if (mapped == null) {
                    skipped += rowNumber
                    return@forEach
                }
                rows += StatementXlsxMappedRow(
                    rowNumber = rowNumber,
                    date = mapped.date,
                    merchant = mapped.merchant,
                    amount = mapped.amount,
                    currency = mapped.currency,
                    type = mapped.type,
                    category = mapped.category,
                    notes = mapped.notes,
                )
            }
        }

        if (firstDetectedColumns == null) {
            return StatementXlsxParseResult.MappingRequired(
                columns = bestMappingHeader,
                reason = "Map date, merchant, and amount/debit/credit columns before previewing this workbook.",
            )
        }
        if (rows.isEmpty() && skipped.isEmpty()) {
            return StatementXlsxParseResult.Failed("XLSX workbook did not include statement transactions.")
        }

        return StatementXlsxParseResult.Done(
            columns = firstDetectedColumns,
            availableColumns = firstAvailableColumns,
            rows = rows,
            skippedRowNumbers = skipped,
        )
    }

    private fun detectHeader(
        grid: List<List<String>>,
        mapping: CsvImportColumnMapping?,
    ): DetectedHeader? =
        grid.asSequence()
            .mapIndexed { index, row ->
                val header = row.trimTrailingBlankCells()
                val columns = StatementCsvMapper.detect(header, mapping)
                if (columns == null) null else DetectedHeader(index, header, columns)
            }
            .filterNotNull()
            .firstOrNull()

    private fun parseWorkbookSheets(entries: Map<String, ByteArray>): List<WorkbookSheet> {
        val workbook = parseXml(entries["xl/workbook.xml"] ?: return emptyList())
        val relationships = parseWorkbookRelationships(entries["xl/_rels/workbook.xml.rels"])
        val sheetElements = workbook.elementsByLocalName("sheet")
        return sheetElements.mapIndexedNotNull { index, sheet ->
            val name = sheet.getAttribute("name").takeIf { it.isNotBlank() } ?: "Sheet ${index + 1}"
            val relationshipId = sheet.getAttribute("r:id")
                .takeIf { it.isNotBlank() }
                ?: sheet.attributesSequence()
                    .firstOrNull { it.first.endsWith(":id") || it.first == "id" }
                    ?.second
            val path = relationshipId
                ?.let(relationships::get)
                ?: "xl/worksheets/sheet${index + 1}.xml"
            WorkbookSheet(name = name, path = path)
        }
    }

    private fun parseWorkbookRelationships(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val doc = parseXml(bytes)
        return doc.elementsByLocalName("Relationship").mapNotNull { rel ->
            val id = rel.getAttribute("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val target = rel.getAttribute("Target").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to normalizeWorkbookTarget(target)
        }.toMap()
    }

    private fun normalizeWorkbookTarget(target: String): String {
        val normalized = target.replace('\\', '/').trimStart('/')
        return if (normalized.startsWith("xl/")) normalized else "xl/$normalized"
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val doc = parseXml(bytes)
        return doc.elementsByLocalName("si").map { it.textContent.orEmpty().trim() }
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = parseXml(bytes)
        return doc.elementsByLocalName("row").map { row ->
            val cells = row.childElements("c")
            val values = mutableListOf<String>()
            cells.forEach { cell ->
                val column = cell.getAttribute("r")
                    .takeWhile(Char::isLetter)
                    .takeIf { it.isNotBlank() }
                    ?.let(::columnIndex)
                    ?: values.size
                while (values.size <= column) values += ""
                values[column] = cellValue(cell, sharedStrings)
            }
            values.trimTrailingBlankCells()
        }
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        val raw = when (type) {
            "inlineStr" -> cell.childElements("is").firstOrNull()?.textContent
            else -> cell.childElements("v").firstOrNull()?.textContent
        }?.trim().orEmpty()

        return when (type) {
            "s" -> raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
            "b" -> if (raw == "1") "TRUE" else "FALSE"
            else -> raw
        }.trim()
    }

    private fun WorkbookSheet.positiveAmountFallbackType(): TxType? {
        val normalized = name.lowercase(Locale.US)
        return when {
            "income" in normalized || "دخل" in normalized -> TxType.INCOME
            "expenses" in normalized || "expense" in normalized || "مصروف" in normalized -> TxType.EXPENSE
            else -> null
        }
    }

    private fun detectedColumns(
        header: List<String>,
        columns: StatementCsvColumns,
        prefix: String,
    ): CsvImportDetectedColumns =
        CsvImportDetectedColumns(
            date = "$prefix ${headerName(header, columns.dateIdx)}",
            merchant = "$prefix ${headerName(header, columns.merchantIdx)}",
            amount = columns.amountIdx?.let { "$prefix ${headerName(header, it)}" },
            debit = columns.debitIdx?.let { "$prefix ${headerName(header, it)}" },
            credit = columns.creditIdx?.let { "$prefix ${headerName(header, it)}" },
            currency = columns.currencyIdx?.let { "$prefix ${headerName(header, it)}" },
            category = columns.categoryIdx?.let { "$prefix ${headerName(header, it)}" },
            type = columns.typeIdx?.let { "$prefix ${headerName(header, it)}" },
            notes = columns.notesIdx?.let { "$prefix ${headerName(header, it)}" },
        )

    private fun headerName(header: List<String>, index: Int): String =
        header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

    private fun columnIndex(raw: String): Int =
        raw.uppercase(Locale.US).fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory) {
                    entries[entry.name.replace('\\', '/')] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun Document.elementsByLocalName(name: String): List<Element> =
        documentElement.elementsByLocalName(name)

    private fun Element.elementsByLocalName(name: String): List<Element> =
        getElementsByTagNameNS("*", name)
            .let { nodes -> List(nodes.length) { nodes.item(it) } }
            .filterIsInstance<Element>()

    private fun Element.childElements(localName: String): List<Element> =
        childNodes
            .let { nodes -> List(nodes.length) { nodes.item(it) } }
            .filterIsInstance<Element>()
            .filter { it.localName == localName || it.nodeName == localName }

    private fun Element.attributesSequence(): Sequence<Pair<String, String>> =
        sequence {
            val attrs = attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                yield(attr.nodeName to attr.nodeValue)
            }
        }

    private fun List<String>.trimTrailingBlankCells(): List<String> {
        val lastNonBlank = indexOfLast { it.isNotBlank() }
        return if (lastNonBlank < 0) emptyList() else take(lastNonBlank + 1)
    }

    private data class WorkbookSheet(val name: String, val path: String)
    private data class DetectedHeader(
        val rowIndex: Int,
        val header: List<String>,
        val columns: StatementCsvColumns,
    )
}
