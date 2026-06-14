package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.repo.WishlistImportPreviewResult
import com.athar.core.domain.repo.WishlistImportResult
import com.athar.core.domain.repo.WishlistRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WishlistImporterTest {

    @Test
    fun `preview reads wishlist sheet and matches existing items by name`() = runTest {
        val repository = FakeWishlistRepository(
            listOf(
                wishlistItem(
                    id = "existing-phone",
                    name = "Phone",
                    cost = Money.of("4500"),
                    notes = "Keep existing note",
                ),
            ),
        )
        val importer = WishlistImporter(repository, FixedClock)

        val result = importer.preview(ByteArrayInputStream(tmoapWishlistWorkbook))

        val preview = (result as WishlistImportPreviewResult.Done).preview
        assertThat(preview.itemRows).isEqualTo(2)
        assertThat(preview.newItems).isEqualTo(1)
        assertThat(preview.updatedItems).isEqualTo(1)
        assertThat(preview.skipped).isEqualTo(3)
        assertThat(preview.totalCost.amount.compareTo(Money.of("11000").amount)).isEqualTo(0)
        assertThat(preview.totalSaved.amount.compareTo(Money.of("1500").amount)).isEqualTo(0)
        assertThat(preview.sampleRows.map { it.name }).containsExactly("Travel", "phone").inOrder()
        assertThat(preview.sampleRows.map { it.existing }).containsExactly(false, true).inOrder()
        assertThat(preview.sampleRows.first().startMonth).isEqualTo(YearMonth.of(2025, 9))
        assertThat(preview.sampleRows.first().desiredMonths).isEqualTo(5)
        assertThat(preview.sampleRows[1].startMonth).isEqualTo(YearMonth.of(2026, 2))
        assertThat(preview.skippedRows.map { it.label })
            .containsExactly("Laptop", "Camera", "Desk")
            .inOrder()
    }

    @Test
    fun `import upserts parsed rows and preserves existing ids`() = runTest {
        val repository = FakeWishlistRepository(
            listOf(wishlistItem(id = "existing-phone", name = "Phone", notes = "Keep existing note")),
        )
        val importer = WishlistImporter(repository, FixedClock)

        val result = importer.import(ByteArrayInputStream(tmoapWishlistWorkbook))

        assertThat(result).isEqualTo(
            WishlistImportResult.Done(imported = 2, newItems = 1, updatedItems = 1, skipped = 3),
        )
        assertThat(repository.upserts.map { it.name }).containsExactly("Travel", "phone").inOrder()
        assertThat(repository.upserts[1].id).isEqualTo("existing-phone")
        assertThat(repository.upserts[1].cost.amount.compareTo(Money.of("6000").amount)).isEqualTo(0)
        assertThat(repository.upserts[1].notes).isEqualTo("Keep existing note")
    }

    @Test
    fun `blank start month defaults to current month from clock`() = runTest {
        val repository = FakeWishlistRepository()
        val importer = WishlistImporter(repository, FixedClock)
        val workbook = xlsxWorkbook(
            XlsxSheet(
                name = "Wishlist",
                rows = listOf(
                    listOf("Item", "Cost", "Current Saved", "Desired Months (optional)", "Start Month"),
                    listOf("Tablet", "2500", "0", "", ""),
                ),
            ),
        )

        val preview = (importer.preview(ByteArrayInputStream(workbook)) as WishlistImportPreviewResult.Done).preview

        assertThat(preview.sampleRows.single().startMonth).isEqualTo(YearMonth.of(2026, 6))
    }

    @Test
    fun `preview rejects non xlsx input`() = runTest {
        val importer = WishlistImporter(FakeWishlistRepository(), FixedClock)

        val result = importer.preview(ByteArrayInputStream("Item,Cost\nTravel,5000".toByteArray()))

        assertThat(result).isEqualTo(
            WishlistImportPreviewResult.Failed("Select a TMOAP .xlsx workbook to import wishlist items."),
        )
    }

    private class FakeWishlistRepository(
        initial: List<WishlistItem> = emptyList(),
    ) : WishlistRepository {
        private val rows = MutableStateFlow(initial)
        val upserts = mutableListOf<WishlistItem>()

        override fun observeAll(): Flow<List<WishlistItem>> = rows

        override suspend fun upsert(item: WishlistItem) {
            upserts += item
            rows.value = rows.value
                .filterNot { it.id == item.id }
                .plus(item)
        }

        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private companion object {
        val FixedClock: Clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-06-14T00:00:00Z")
        }

        val tmoapWishlistWorkbook = xlsxWorkbook(
            XlsxSheet(
                name = "Wishlist",
                rows = listOf(
                    listOf("A", "B"),
                    listOf("Capacity Basis", "", "", "", "", "", "", "", "", "", "Over-Budget Categories"),
                    listOf("Monthly Income", "30170.5"),
                    listOf("Monthly Expenses", "28710.66"),
                    listOf("Savings to Preserve (override)", "5000"),
                    listOf("Savings to Preserve (effective)", "5000"),
                    listOf("Monthly Wishlist Capacity", "0"),
                    emptyList(),
                    emptyList(),
                    listOf(
                        "Item",
                        "Cost",
                        "Current Saved",
                        "Desired Months (optional)",
                        "Monthly Save Needed",
                        "Cap.-Based Months",
                        "Projected Purchase Month",
                        "Feasible?",
                        "Shortfall/mo",
                        "Status (Portfolio)",
                        "Start Month",
                        "Finish Month",
                    ),
                    listOf("Travel", "5000.0", "1000.0", "5", "", "", "", "", "", "NOW", "45901.0", "46023.0"),
                    listOf("phone", "6000", "500", "", "", "", "", "", "", "WAIT until 2026-02", "2026-02", ""),
                    listOf("Laptop", "not-money", "0", "", "", "", "", "", "", "", "2026-07", ""),
                    listOf("Camera", "3000", "0", "2.5", "", "", "", "", "", "", "2026-08", ""),
                    listOf("Desk", "1200", "0", "", "", "", "", "", "", "", "soon", ""),
                ),
            ),
        )

        fun wishlistItem(
            id: String,
            name: String,
            cost: Money = Money.of("1000"),
            currentSaved: Money = Money.zero(),
            desiredMonths: Int? = null,
            startMonth: YearMonth = YearMonth.of(2026, 1),
            notes: String? = null,
        ): WishlistItem = WishlistItem(
            id = id,
            name = name,
            cost = cost,
            currentSaved = currentSaved,
            desiredMonths = desiredMonths,
            startMonth = startMonth,
            notes = notes,
        )

        private fun xlsxWorkbook(vararg sheets: XlsxSheet): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putText(
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      ${sheets.indices.joinToString("\n") { i ->
                        """<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                    }}
                    </Types>
                    """.trimIndent(),
                )
                zip.putText(
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """.trimIndent(),
                )
                zip.putText(
                    "xl/workbook.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets>
                        ${sheets.mapIndexed { i, sheet ->
                        """<sheet name="${sheet.name.xmlEscape()}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
                    }.joinToString("\n")}
                      </sheets>
                    </workbook>
                    """.trimIndent(),
                )
                zip.putText(
                    "xl/_rels/workbook.xml.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      ${sheets.indices.joinToString("\n") { i ->
                        """<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
                    }}
                    </Relationships>
                    """.trimIndent(),
                )
                sheets.forEachIndexed { index, sheet ->
                    zip.putText("xl/worksheets/sheet${index + 1}.xml", sheet.toWorksheetXml())
                }
            }
            return out.toByteArray()
        }

        private fun ZipOutputStream.putText(name: String, text: String) {
            putNextEntry(ZipEntry(name))
            write(text.toByteArray(Charsets.UTF_8))
            closeEntry()
        }

        private fun XlsxSheet.toWorksheetXml(): String {
            val body = rows.mapIndexed { rowIndex, row ->
                val cells = row.mapIndexedNotNull { columnIndex, value ->
                    value.takeIf { it.isNotBlank() }?.let {
                        val ref = "${columnName(columnIndex)}${rowIndex + 1}"
                        """<c r="$ref" t="inlineStr"><is><t>${it.xmlEscape()}</t></is></c>"""
                    }
                }.joinToString("")
                """<row r="${rowIndex + 1}">$cells</row>"""
            }.joinToString("\n")
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    $body
                  </sheetData>
                </worksheet>
            """.trimIndent()
        }

        private fun columnName(index: Int): String {
            var current = index + 1
            val out = StringBuilder()
            while (current > 0) {
                val rem = (current - 1) % 26
                out.insert(0, ('A'.code + rem).toChar())
                current = (current - 1) / 26
            }
            return out.toString()
        }

        private fun String.xmlEscape(): String =
            replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
    }

    private data class XlsxSheet(
        val name: String,
        val rows: List<List<String>>,
    )
}
