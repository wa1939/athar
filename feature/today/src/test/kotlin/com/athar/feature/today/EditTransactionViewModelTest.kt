package com.athar.feature.today

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.ReceiptAttachment
import com.athar.core.domain.model.ReceiptAttachmentMeta
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.ReceiptAttachmentRepository
import com.athar.core.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditTransactionViewModelTest {

    private lateinit var mainDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load exposes receipt metadata without loading payload`() = runTest(mainDispatcher) {
        val receipts = FakeReceiptAttachmentRepository(receipt())
        val viewModel = EditTransactionViewModel(FakeCategoryRepository, receipts)

        viewModel.load(Fixtures.transaction(id = "tx-1"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state?.receiptMeta?.originalName).isEqualTo("coffee.jpg")
        assertThat(state?.receiptMeta?.sizeBytes).isEqualTo(3)
        assertThat(state?.receiptPreview).isNull()
    }

    @Test
    fun `viewer loads the stored receipt payload`() = runTest(mainDispatcher) {
        val receipts = FakeReceiptAttachmentRepository(receipt(payload = byteArrayOf(9, 8, 7)))
        val viewModel = EditTransactionViewModel(FakeCategoryRepository, receipts)

        viewModel.load(Fixtures.transaction(id = "tx-1"))
        advanceUntilIdle()
        viewModel.openReceiptViewer()
        advanceUntilIdle()

        val preview = viewModel.state.value?.receiptPreview
        assertThat(preview?.payload).isEqualTo(byteArrayOf(9, 8, 7))
        assertThat(viewModel.state.value?.receiptStatus).isNull()
    }

    @Test
    fun `delete receipt clears metadata and preview without touching transaction state`() = runTest(mainDispatcher) {
        val receipts = FakeReceiptAttachmentRepository(receipt())
        val viewModel = EditTransactionViewModel(FakeCategoryRepository, receipts)

        viewModel.load(Fixtures.transaction(id = "tx-1"))
        advanceUntilIdle()
        viewModel.openReceiptViewer()
        advanceUntilIdle()
        viewModel.deleteReceipt()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(receipts.current).isNull()
        assertThat(state?.original?.id).isEqualTo("tx-1")
        assertThat(state?.receiptMeta).isNull()
        assertThat(state?.receiptPreview).isNull()
        assertThat(state?.receiptStatus).isEqualTo(EditReceiptStatus.Deleted)
    }

    @Test
    fun `receipt export names keep original names safe and add fallback extensions`() {
        val unsafe = meta(originalName = "folder/sub:receipt?.jpg", mimeType = "image/jpeg")
        val missingExtension = meta(originalName = "scan", mimeType = "image/png")
        val fallback = meta(originalName = null, mimeType = "image/webp")

        assertThat(receiptExportFileName(unsafe)).isEqualTo("sub_receipt_.jpg")
        assertThat(receiptExportFileName(missingExtension)).isEqualTo("scan.png")
        assertThat(receiptExportFileName(fallback)).isEqualTo("athar-receipt-tx-12345.webp")
    }

    private fun receipt(
        transactionId: String = "tx-1",
        payload: ByteArray = byteArrayOf(1, 2, 3),
    ): ReceiptAttachment = ReceiptAttachment(
        id = "receipt-1",
        transactionId = transactionId,
        mimeType = "image/jpeg",
        originalName = "coffee.jpg",
        sizeBytes = payload.size.toLong(),
        payload = payload,
        createdAt = Instant.parse("2026-06-13T12:00:00Z"),
    )

    private fun meta(
        originalName: String?,
        mimeType: String,
    ): ReceiptAttachmentMeta = ReceiptAttachmentMeta(
        id = "receipt-1",
        transactionId = "tx-12345",
        mimeType = mimeType,
        originalName = originalName,
        sizeBytes = 42,
        createdAt = Instant.parse("2026-06-13T12:00:00Z"),
    )
}

private object FakeCategoryRepository : CategoryRepository {
    override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> =
        flowOf(listOf(Fixtures.category(kind = CategoryKind.EXPENSE)))

    override suspend fun get(id: String): Category? = null
    override suspend fun upsert(category: Category) = Unit
    override suspend fun archive(id: String) = Unit
    override suspend fun reorder(ids: List<String>) = Unit
}

private class FakeReceiptAttachmentRepository(
    var current: ReceiptAttachment?,
) : ReceiptAttachmentRepository {
    override suspend fun upsert(attachment: ReceiptAttachment) {
        current = attachment
    }

    override suspend fun getForTransaction(transactionId: String): ReceiptAttachment? =
        current?.takeIf { it.transactionId == transactionId }

    override suspend fun metadataForTransaction(transactionId: String): ReceiptAttachmentMeta? =
        getForTransaction(transactionId)?.let {
            ReceiptAttachmentMeta(
                id = it.id,
                transactionId = it.transactionId,
                mimeType = it.mimeType,
                originalName = it.originalName,
                sizeBytes = it.sizeBytes,
                createdAt = it.createdAt,
            )
        }

    override suspend fun deleteForTransaction(transactionId: String) {
        if (current?.transactionId == transactionId) current = null
    }
}
