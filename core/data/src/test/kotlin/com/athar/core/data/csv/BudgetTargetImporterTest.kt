package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.repo.BudgetTargetImportPreviewResult
import com.athar.core.domain.repo.BudgetTargetImportResult
import com.athar.core.domain.repo.CategoryRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BudgetTargetImporterTest {

    @Test
    fun `preview reads budget target sheet and resolves tmoap aliases by kind`() = runTest {
        val categories = FakeCategoryRepository(tmoapCategories())
        val importer = BudgetTargetImporter(categories)

        val result = importer.preview(ByteArrayInputStream(tmoapBudgetWorkbook))

        val preview = (result as BudgetTargetImportPreviewResult.Done).preview
        assertThat(preview.targetRows).isEqualTo(4)
        assertThat(preview.changed).isEqualTo(3)
        assertThat(preview.skipped).isEqualTo(2)
        assertThat(preview.expenseTargets).isEqualTo(2)
        assertThat(preview.incomeTargets).isEqualTo(2)
        assertThat(preview.monthlyExpenseTotal.amount.compareTo(Money.of("1080").amount)).isEqualTo(0)
        assertThat(preview.monthlyIncomeTotal.amount.compareTo(Money.of("27725").amount)).isEqualTo(0)
        assertThat(preview.sampleRows.map { it.categoryId })
            .containsExactly("cat-public-transport", "cat-wife-allowance", "cat-salary", "cat-other-income")
            .inOrder()
        assertThat(preview.sampleRows.map { it.changed })
            .containsExactly(false, true, true, true)
            .inOrder()
        assertThat(preview.skippedRows.map { it.label })
            .containsExactly("Unknown Expense", "Side project")
            .inOrder()
    }

    @Test
    fun `import applies matched monthly targets only after confirmation`() = runTest {
        val categories = FakeCategoryRepository(tmoapCategories())
        val importer = BudgetTargetImporter(categories)

        val result = importer.import(ByteArrayInputStream(tmoapBudgetWorkbook))

        assertThat(result).isEqualTo(BudgetTargetImportResult.Done(applied = 4, changed = 3, skipped = 2))
        assertThat(categories.upserts.map { it.id })
            .containsExactly("cat-public-transport", "cat-wife-allowance", "cat-salary", "cat-other-income")
            .inOrder()
        assertThat(categories.upserts.map { it.monthlyTarget?.amount?.toPlainString() })
            .containsExactly("80.00", "1000.00", "27700.00", "25.00")
            .inOrder()
    }

    @Test
    fun `preview rejects non xlsx input`() = runTest {
        val importer = BudgetTargetImporter(FakeCategoryRepository(tmoapCategories()))

        val result = importer.preview(ByteArrayInputStream("category,target\nRent,1000".toByteArray()))

        assertThat(result).isEqualTo(
            BudgetTargetImportPreviewResult.Failed("Select a TMOAP .xlsx workbook to import budget targets."),
        )
    }

    private class FakeCategoryRepository(
        initial: List<Category>,
    ) : CategoryRepository {
        private val rows = MutableStateFlow(initial)
        val upserts = mutableListOf<Category>()

        override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> =
            rows

        override suspend fun get(id: String): Category? = rows.value.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category) {
            upserts += category
            rows.value = rows.value.map { if (it.id == category.id) category else it }
        }

        override suspend fun archive(id: String) = Unit
        override suspend fun reorder(ids: List<String>) = Unit
    }

    private companion object {
        val tmoapBudgetWorkbook = xlsxWorkbook(
            XlsxSheet(
                name = "Budget Targets",
                rows = listOf(
                    listOf("", "Expense Categories", "", "", "", "", "Monthly Spend"),
                    listOf("1", "Public transportation", "", "", "", "", "80"),
                    listOf("2", "Wife", "", "", "", "", "1000"),
                    listOf("3", "Unknown Expense", "", "", "", "", "12"),
                    listOf("", "Total Expenses", "", "", "", "", "1092"),
                    listOf("", "Income Categories", "", "", "", "", "Monthly Income"),
                    listOf("1", "Job", "", "", "", "", "27700"),
                    listOf("2", "Other", "", "", "", "", "25"),
                    listOf("3", "Side project", "", "", "", "", "not-money"),
                ),
            ),
        )

        fun tmoapCategories(): List<Category> = listOf(
            category(
                id = "cat-public-transport",
                name = "Public transport",
                kind = CategoryKind.EXPENSE,
                monthlyTarget = Money.of("80.00"),
            ),
            category("cat-wife-allowance", "Wife allowance", CategoryKind.EXPENSE),
            category("cat-salary", "Salary", CategoryKind.INCOME),
            category("cat-other-income", "Other income", CategoryKind.INCOME),
        )

        fun category(
            id: String,
            name: String,
            kind: CategoryKind,
            monthlyTarget: Money? = null,
        ): Category = Category(
            id = id,
            name = name,
            nameAr = name,
            kind = kind,
            icon = null,
            monthlyTarget = monthlyTarget,
            archived = false,
            sortOrder = 0,
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
