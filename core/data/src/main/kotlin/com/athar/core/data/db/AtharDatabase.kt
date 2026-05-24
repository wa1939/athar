package com.athar.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.athar.core.data.db.dao.AccountDao
import com.athar.core.data.db.dao.ActivityLogDao
import com.athar.core.data.db.dao.CategoryDao
import com.athar.core.data.db.dao.CategoryRuleDao
import com.athar.core.data.db.dao.InvestmentDao
import com.athar.core.data.db.dao.SmsMessageDao
import com.athar.core.data.db.dao.TransactionDao
import com.athar.core.data.db.dao.WishlistDao
import com.athar.core.data.db.entity.AccountEntity
import com.athar.core.data.db.entity.ActivityLogEntity
import com.athar.core.data.db.entity.CategoryEntity
import com.athar.core.data.db.entity.CategoryRuleEntity
import com.athar.core.data.db.entity.InvestmentContributionEntity
import com.athar.core.data.db.entity.InvestmentPoolEntity
import com.athar.core.data.db.entity.SmsMessageEntity
import com.athar.core.data.db.entity.TransactionEntity
import com.athar.core.data.db.entity.WishlistEntity

@Database(
    version = 2,
    exportSchema = true,
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CategoryRuleEntity::class,
        WishlistEntity::class,
        InvestmentPoolEntity::class,
        InvestmentContributionEntity::class,
        SmsMessageEntity::class,
        ActivityLogEntity::class,
    ],
)
@TypeConverters(Converters::class)
internal abstract class AtharDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        internal const val NAME: String = "athar.db"

        /**
         * v1 → v2: adds the `activity_log` table (P-08).
         */
        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_log (
                        id TEXT NOT NULL PRIMARY KEY,
                        timestamp INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        summary TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_log_timestamp ON activity_log(timestamp)")
            }
        }
    }
}
