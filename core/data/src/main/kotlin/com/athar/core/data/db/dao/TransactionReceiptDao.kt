package com.athar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.athar.core.data.db.entity.TransactionReceiptEntity
import com.athar.core.data.db.entity.TransactionReceiptMetaRow

@Dao
internal interface TransactionReceiptDao {

    @Query("SELECT * FROM transaction_receipt WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getForTransaction(transactionId: String): TransactionReceiptEntity?

    @Query(
        """
        SELECT id, transactionId, mimeType, originalName, sizeBytes, createdAt
        FROM transaction_receipt
        WHERE transactionId = :transactionId
        LIMIT 1
        """,
    )
    suspend fun metadataForTransaction(transactionId: String): TransactionReceiptMetaRow?

    @Query("SELECT * FROM transaction_receipt ORDER BY createdAt DESC")
    suspend fun all(): List<TransactionReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionReceiptEntity)

    @Query("DELETE FROM transaction_receipt WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: String)

    @Query("DELETE FROM transaction_receipt")
    suspend fun clear()
}
