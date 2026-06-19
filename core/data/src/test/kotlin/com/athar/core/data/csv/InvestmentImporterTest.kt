package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.repo.InvestmentImportPreviewResult
import com.athar.core.domain.repo.InvestmentImportResult
import com.athar.core.domain.repo.InvestmentRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InvestmentImporterTest {

    @Test
    fun `preview reads family investments sheet without double counting summary rows`() = runTest {
        val repository = FakeInvestmentRepository(
            pools = listOf(pool(id = "existing-pool", name = "استثمارات العائلة")),
            contributions = listOf(
                contribution(id = "old-1", poolId = "existing-pool", owner = "Old", amount = "1"),
                contribution(id = "old-2", poolId = "existing-pool", owner = "Old 2", amount = "2"),
            ),
        )
        val importer = InvestmentImporter(repository)

        val result = importer.preview(ByteArrayInputStream(tmoapInvestmentsWorkbook))

        val preview = (result as InvestmentImportPreviewResult.Done).preview
        assertThat(preview.poolName).isEqualTo("استثمارات العائلة")
        assertThat(preview.period).isEqualTo("3 أشهر")
        assertThat(preview.existingPool).isTrue()
        assertThat(preview.contributionRows).isEqualTo(3)
        assertThat(preview.replacedContributions).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(preview.totalCorpus.amount.compareTo(Money.of("2200").amount)).isEqualTo(0)
        assertThat(preview.totalReturn.amount.compareTo(Money.of("200").amount)).isEqualTo(0)
        assertThat(preview.sampleRows.map { it.ownerName })
            .containsExactly("وليد", "هناء", "حامد")
            .inOrder()
        assertThat(preview.skippedRows.single().label).isEqualTo("وافي")
    }

    @Test
    fun `import replaces matched pool contributions instead of appending duplicates`() = runTest {
        val repository = FakeInvestmentRepository(
            pools = listOf(pool(id = "existing-pool", name = "استثمارات العائلة")),
            contributions = listOf(
                contribution(id = "old-1", poolId = "existing-pool", owner = "Old", amount = "1"),
                contribution(id = "old-2", poolId = "existing-pool", owner = "Old 2", amount = "2"),
            ),
        )
        val importer = InvestmentImporter(repository)

        val result = importer.import(ByteArrayInputStream(tmoapInvestmentsWorkbook))

        assertThat(result).isEqualTo(
            InvestmentImportResult.Done(
                importedContributions = 3,
                replacedContributions = 2,
                skipped = 1,
                existingPool = true,
            ),
        )
        assertThat(repository.deletedContributionIds).containsExactly("old-1", "old-2").inOrder()
        assertThat(repository.upsertedPools.single().id).isEqualTo("existing-pool")
        assertThat(repository.upsertedPools.single().totalReturn.amount.compareTo(Money.of("200").amount))
            .isEqualTo(0)
        assertThat(repository.upsertedContributions.map { it.poolId }).containsExactly(
            "existing-pool",
            "existing-pool",
            "existing-pool",
        )
        assertThat(repository.upsertedContributions.map { it.ownerName })
            .containsExactly("وليد", "هناء", "حامد")
            .inOrder()
    }

    @Test
    fun `preview creates a new pool when no matching pool exists`() = runTest {
        val importer = InvestmentImporter(FakeInvestmentRepository())

        val preview =
            (importer.preview(ByteArrayInputStream(tmoapInvestmentsWorkbook)) as InvestmentImportPreviewResult.Done)
                .preview

        assertThat(preview.existingPool).isFalse()
        assertThat(preview.replacedContributions).isEqualTo(0)
    }

    @Test
    fun `preview rejects non xlsx input`() = runTest {
        val importer = InvestmentImporter(FakeInvestmentRepository())

        val result = importer.preview(ByteArrayInputStream("owner,amount\nWaleed,1000".toByteArray()))

        assertThat(result).isEqualTo(
            InvestmentImportPreviewResult.Failed("Select a TMOAP .xlsx workbook to import family investments."),
        )
    }

    private class FakeInvestmentRepository(
        pools: List<InvestmentPool> = emptyList(),
        contributions: List<InvestmentContribution> = emptyList(),
    ) : InvestmentRepository {
        private val pools = MutableStateFlow(pools)
        private val contributions = MutableStateFlow(contributions)
        val upsertedPools = mutableListOf<InvestmentPool>()
        val upsertedContributions = mutableListOf<InvestmentContribution>()
        val deletedContributionIds = mutableListOf<String>()

        override fun observePoolsWithContributions(): Flow<Map<InvestmentPool, List<InvestmentContribution>>> =
            combine(pools, contributions) { pools, contributions ->
                pools.associateWith { pool -> contributions.filter { it.poolId == pool.id } }
            }

        override suspend fun upsertPool(pool: InvestmentPool) {
            upsertedPools += pool
            pools.value = pools.value.filterNot { it.id == pool.id }.plus(pool)
        }

        override suspend fun upsertContribution(contribution: InvestmentContribution) {
            upsertedContributions += contribution
            contributions.value = contributions.value
                .filterNot { it.id == contribution.id }
                .plus(contribution)
        }

        override suspend fun deletePool(id: String) {
            pools.value = pools.value.filterNot { it.id == id }
            contributions.value = contributions.value.filterNot { it.poolId == id }
        }

        override suspend fun deleteContribution(id: String) {
            deletedContributionIds += id
            contributions.value = contributions.value.filterNot { it.id == id }
        }
    }

    private companion object {
        val tmoapInvestmentsWorkbook = xlsxWorkbook(
            XlsxSheet(
                name = "استثمارات العائلة",
                rows = listOf(
                    listOf("المبلغ", "المالك", "", "", "العائد من الاستثمار", "الفترة"),
                    listOf("1000.0", "وليد", "", "", "200.0", "3 أشهر"),
                    listOf("not-money", "وافي", "", "", "", ""),
                    listOf("500.0", "هناء", "", "", "", ""),
                    listOf("700.0", "حامد", "", "", "200.0", "المجموع"),
                    emptyList(),
                    listOf("2200.0", "المجموع", "", "", "", ""),
                    emptyList(),
                    listOf("المبلغ", "المالك", "النسبة", "فوائد العائد", "صافي المبلغ + العوائد"),
                    listOf("1500.0", "وليد", "0.68", "136.36", "1636.36"),
                    listOf("700.0", "حامد", "0.32", "63.64", "763.64"),
                ),
            ),
        )

        fun pool(
            id: String,
            name: String,
            period: String = "Old period",
            totalReturn: Money = Money.zero(),
        ): InvestmentPool = InvestmentPool(
            id = id,
            name = name,
            period = period,
            totalReturn = totalReturn,
        )

        fun contribution(
            id: String,
            poolId: String,
            owner: String,
            amount: String,
        ): InvestmentContribution = InvestmentContribution(
            id = id,
            poolId = poolId,
            ownerName = owner,
            amount = Money.of(amount),
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
