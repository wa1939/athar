package com.athar.core.data.backup

import androidx.room.withTransaction
import com.athar.core.common.crypto.AtharCrypto
import com.athar.core.data.db.AtharDatabase
import com.athar.core.domain.repo.BackupRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup write + restore service.
 *
 * Format: gzip(JSON([BackupSnapshot])) → AES-256-GCM → file.
 * The user-facing extension is `.athar`. See [AtharCrypto] for on-disk layout.
 *
 * Restore strategy: REPLACE — clears existing tables and writes the snapshot atomically
 * via Room transaction. Append/merge land later if user feedback demands them.
 */
@Singleton
class BackupService @Inject internal constructor(
    private val db: AtharDatabase,
    private val clock: Clock,
) : BackupRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun export(output: OutputStream, passphrase: CharArray) {
        val snapshot = capture()
        val plainJson = json.encodeToString(BackupSnapshot.serializer(), snapshot)
        val gzipped = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(plainJson.toByteArray()) }
            baos.toByteArray()
        }
        val encrypted = AtharCrypto.encrypt(gzipped, passphrase)
        output.use { it.write(encrypted) }
        Timber.i(
            "Backup exported: %d tx · %d cat · %d rules · ciphertext %d B",
            snapshot.transactions.size, snapshot.categories.size,
            snapshot.categoryRules.size, encrypted.size,
        )
    }

    override suspend fun import(bytes: ByteArray, passphrase: CharArray) {
        val decrypted = AtharCrypto.decrypt(bytes, passphrase)
        val plainJson = GZIPInputStream(decrypted.inputStream()).bufferedReader().use { it.readText() }
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), plainJson)
        restore(snapshot)
        Timber.i(
            "Backup imported: v%d · %d tx · %d cat",
            snapshot.version, snapshot.transactions.size, snapshot.categories.size,
        )
    }

    private suspend fun capture(): BackupSnapshot = BackupSnapshot(
        exportedAt = clock.now(),
        accounts = db.accountDao().all().map { it.toBackup() },
        categories = db.categoryDao().all().map { it.toBackup() },
        transactions = db.transactionDao().all().map { it.toBackup() },
        categoryRules = db.categoryRuleDao().all().map { it.toBackup() },
        wishlist = db.wishlistDao().all().map { it.toBackup() },
        investmentPools = db.investmentDao().allPools().map { it.toBackup() },
        investmentContributions = db.investmentDao().allContributions().map { it.toBackup() },
        smsAudit = db.smsMessageDao().all().map { it.toBackup() },
    )

    private suspend fun restore(snapshot: BackupSnapshot) {
        db.withTransaction {
            // Replace strategy: clear children before parents to satisfy FKs, then re-insert.
            db.transactionDao().clear()
            db.categoryRuleDao().clear()
            db.wishlistDao().clear()
            db.investmentDao().clearContributions()
            db.investmentDao().clearPools()
            db.smsMessageDao().clear()
            db.categoryDao().clear()
            db.accountDao().clear()

            snapshot.accounts.forEach { db.accountDao().upsert(it.toEntity()) }
            db.categoryDao().upsertAll(snapshot.categories.map { it.toEntity() })
            snapshot.categoryRules.forEach { db.categoryRuleDao().upsert(it.toEntity()) }
            snapshot.transactions.forEach { db.transactionDao().upsert(it.toEntity()) }
            snapshot.wishlist.forEach { db.wishlistDao().upsert(it.toEntity()) }
            snapshot.investmentPools.forEach { db.investmentDao().upsertPool(it.toEntity()) }
            snapshot.investmentContributions.forEach { db.investmentDao().upsertContribution(it.toEntity()) }
            snapshot.smsAudit.forEach { db.smsMessageDao().upsert(it.toEntity()) }
        }
    }
}
