package com.athar.core.data.repo

import com.athar.core.common.money.Money
import com.athar.core.data.db.dao.AccountDao
import com.athar.core.data.db.dao.TransactionDao
import com.athar.core.data.mapper.toDomain
import com.athar.core.data.mapper.toEntity
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.repo.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val txDao: TransactionDao,
    private val clock: Clock,
) : AccountRepository {

    override fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(includeArchived: Boolean): Flow<List<Account>> =
        if (includeArchived) {
            accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }
        } else {
            observeActive()
        }

    override suspend fun get(id: String): Account? = accountDao.get(id)?.toDomain()

    override suspend fun upsert(account: Account) {
        val now = clock.now()
        accountDao.upsert(account.copy(updatedAt = now).toEntity())
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val now = clock.now()
        accountDao.setArchived(
            id = id,
            archivedAt = if (archived) now else null,
            active = !archived,
            now = now,
        )
    }

    override suspend fun delete(id: String) {
        // The manual seed account must always exist for FK targets — protect it.
        if (id == MANUAL_ACCOUNT_ID) {
            setArchived(id, archived = true)
            return
        }
        // RESTRICT FK on transactions.accountId will throw if rows still reference id.
        // Caller (ViewModel) should pre-check via [observeBalances] / a UI guard. We let
        // the SQLiteConstraintException bubble so it's visible rather than silently fail.
        accountDao.delete(id)
    }

    override fun observeBalances(): Flow<List<AccountBalance>> = combine(
        accountDao.observeAll(),
        txDao.observeBalancesByAccount(),
    ) { accounts, sums ->
        val sumMap: Map<Pair<String, String>, Long> = sums
            .associate { (it.accountId to it.currency) to it.sumMinor }
        accounts.map { entity ->
            val account = entity.toDomain()
            val key = account.id to account.openingBalance.currency
            val confirmedMinor = sumMap[key] ?: 0L
            val current = account.openingBalance + Money.ofMinor(confirmedMinor, account.openingBalance.currency)
            AccountBalance(account = account, current = current)
        }
    }

    override fun observeNetWorth(displayCurrency: String): Flow<NetWorth> =
        observeBalances().map { balances ->
            // Exclude archived from the net-worth headline; show them only on the Accounts screen.
            val active = balances.filter { !it.account.archived }
            val byCurrency: Map<String, Money> = active
                .groupBy { it.current.currency }
                .mapValues { (currency, group) ->
                    Money.sumAmounts(group.map { it.current }, currency)
                }
            val total = Money.sumAmounts(active.map { it.current }, displayCurrency)
            NetWorth(total = total, byCurrency = byCurrency, accounts = active)
        }
}
