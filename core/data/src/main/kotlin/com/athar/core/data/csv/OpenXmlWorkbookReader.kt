package com.athar.core.data.csv

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal data class OpenXmlWorkbook(
    val entries: Map<String, ByteArray>,
    val sharedStrings: List<String>,
    val sheets: List<OpenXmlSheet>,
) {
    fun rows(sheet: OpenXmlSheet): List<List<String>>? =
        entries[sheet.path]?.let { OpenXmlWorkbookReader.parseWorksheet(it, sharedStrings) }
}

internal data class OpenXmlSheet(
    val name: String,
    val path: String,
)

internal object OpenXmlWorkbookReader {

    fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    fun read(bytes: ByteArray): OpenXmlWorkbook {
        val entries = unzip(bytes)
        require("xl/workbook.xml" in entries) { "XLSX workbook is missing xl/workbook.xml." }

        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheets = parseWorkbookSheets(entries)
        require(sheets.isNotEmpty()) { "XLSX workbook did not include worksheets." }

        return OpenXmlWorkbook(
            entries = entries,
            sharedStrings = sharedStrings,
            sheets = sheets,
        )
    }

    internal fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
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

    private fun parseWorkbookSheets(entries: Map<String, ByteArray>): List<OpenXmlSheet> {
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
            OpenXmlSheet(name = name, path = path)
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

    private fun columnIndex(raw: String): Int =
        raw.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

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
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes.withoutLeadingXmlPadding()))
    }

    private fun ByteArray.withoutLeadingXmlPadding(): ByteArray {
        var index = 0
        if (
            size >= 3 &&
            this[0] == 0xEF.toByte() &&
            this[1] == 0xBB.toByte() &&
            this[2] == 0xBF.toByte()
        ) {
            index = 3
        }
        while (index < size && this[index] in XmlLeadingPaddingBytes) {
            index += 1
        }
        return if (index == 0) this else copyOfRange(index, size)
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

    private val XmlLeadingPaddingBytes = byteArrayOf(
        ' '.code.toByte(),
        '\t'.code.toByte(),
        '\n'.code.toByte(),
        '\r'.code.toByte(),
    )
}
