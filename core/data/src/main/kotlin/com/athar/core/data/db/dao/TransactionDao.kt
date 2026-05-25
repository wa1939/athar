package com.athar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.athar.core.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
internal interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE date >= :start AND date < :endExclusive
          AND (:status IS NULL OR status = :status)
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeByPeriod(start: LocalDate, endExclusive: LocalDate, status: String?): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED' AND date >= :since ORDER BY date DESC")
    fun observeConfirmedSince(since: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT id FROM transactions LIMIT 1")
    suspend fun firstId(): String?

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("DELETE FROM transactions WHERE status = 'PENDING'")
    suspend fun clearPending(): Int

    @Query("UPDATE transactions SET status = 'CONFIRMED', updatedAt = :now WHERE status = 'PENDING' AND confidence >= :minConfidence")
    suspend fun confirmAllConfident(minConfidence: Float, now: kotlinx.datetime.Instant): Int

    @Query("UPDATE transactions SET status = 'DISMISSED', updatedAt = :now WHERE status = 'PENDING' AND confidence < :maxConfidence")
    suspend fun dismissAllLowConfidence(maxConfidence: Float, now: kotlinx.datetime.Instant): Int

    @Query("UPDATE transactions SET status = 'DISMISSED', updatedAt = :now WHERE status = 'PENDING'")
    suspend fun dismissAllPending(now: kotlinx.datetime.Instant): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE transactions SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: kotlinx.datetime.Instant)
}
