package dev.personal.ledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC") fun transactions(): Flow<List<Txn>>
    @Query("SELECT * FROM splits") fun splits(): Flow<List<Split>>
    @Query("SELECT * FROM presets ORDER BY sortOrder, id") fun presets(): Flow<List<Preset>>
    @Query("SELECT * FROM recurring ORDER BY nextDue, id") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM price_changes ORDER BY effectiveDay") fun priceChanges(): Flow<List<PriceChange>>
    @Query("SELECT * FROM installments ORDER BY firstDue, id") fun installments(): Flow<List<Installment>>

    @Query("SELECT * FROM recurring") suspend fun recurringNow(): List<Recurring>
    @Query("SELECT * FROM installments") suspend fun installmentsNow(): List<Installment>
    @Query("SELECT COUNT(*) FROM transactions WHERE installmentId = :id") suspend fun installmentPayments(id: Long): Int
    @Query("SELECT COUNT(*) FROM accounts") suspend fun accountCount(): Int

    @Upsert suspend fun upsert(a: Account): Long
    @Upsert suspend fun upsert(c: Category): Long
    @Upsert suspend fun upsert(t: Txn): Long
    @Upsert suspend fun upsert(p: Preset): Long
    @Upsert suspend fun upsert(r: Recurring): Long
    @Upsert suspend fun upsert(i: Installment): Long
    @Upsert suspend fun upsert(p: PriceChange): Long
    @Upsert suspend fun upsertSplits(s: List<Split>)
    @Upsert suspend fun upsertPresets(p: List<Preset>)

    @Delete suspend fun delete(t: Txn)
    @Delete suspend fun delete(p: Preset)
    @Delete suspend fun delete(r: Recurring)
    @Delete suspend fun delete(i: Installment)
    @Query("DELETE FROM splits WHERE txId = :txId") suspend fun deleteSplits(txId: Long)
    @Query("SELECT * FROM splits WHERE txId = :txId") suspend fun splitsOf(txId: Long): List<Split>

    @Transaction
    suspend fun replaceSplits(txId: Long, lines: List<Split>) {
        deleteSplits(txId)
        if (lines.isNotEmpty()) upsertSplits(lines.map { it.copy(id = 0, txId = txId) })
    }

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM splits") suspend fun clearSplits()
    @Query("DELETE FROM presets") suspend fun clearPresets()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM price_changes") suspend fun clearPriceChanges()
    @Query("DELETE FROM installments") suspend fun clearInstallments()

    @Upsert suspend fun insertAccounts(v: List<Account>)
    @Upsert suspend fun insertCategories(v: List<Category>)
    @Upsert suspend fun insertTransactions(v: List<Txn>)
    @Upsert suspend fun insertSplits(v: List<Split>)
    @Upsert suspend fun insertRecurring(v: List<Recurring>)
    @Upsert suspend fun insertPriceChanges(v: List<PriceChange>)
    @Upsert suspend fun insertInstallments(v: List<Installment>)

    /** Replaces the whole ledger atomically (restore, demo data, reset). */
    @Transaction
    suspend fun replaceAll(d: LedgerData) {
        clearSplits(); clearTransactions(); clearPresets(); clearPriceChanges()
        clearRecurring(); clearInstallments(); clearCategories(); clearAccounts()
        insertAccounts(d.accounts); insertCategories(d.categories); insertTransactions(d.transactions)
        insertSplits(d.splits); upsertPresets(d.presets); insertRecurring(d.recurring)
        insertPriceChanges(d.priceChanges); insertInstallments(d.installments)
    }
}

@Database(
    entities = [Account::class, Category::class, Txn::class, Split::class, Preset::class,
        Recurring::class, PriceChange::class, Installment::class],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun open(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db").build()
    }
}

/** Everything in the ledger, in memory. A personal ledger is thousands of rows, so pure-Kotlin maths over it is cheap. */
@kotlinx.serialization.Serializable
data class LedgerData(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Txn> = emptyList(),
    val splits: List<Split> = emptyList(),
    val presets: List<Preset> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val priceChanges: List<PriceChange> = emptyList(),
    val installments: List<Installment> = emptyList(),
) {
    @kotlinx.serialization.Transient val accountById = accounts.associateBy { it.id }
    @kotlinx.serialization.Transient val categoryById = categories.associateBy { it.id }
    @kotlinx.serialization.Transient val splitsByTx = splits.groupBy { it.txId }
    @kotlinx.serialization.Transient val presetById = presets.associateBy { it.id }
    @kotlinx.serialization.Transient val txById = transactions.associateBy { it.id }

    /** Top-level category for analytics roll-ups. */
    fun rootOf(categoryId: Long?): Category? {
        var c = categoryId?.let { categoryById[it] } ?: return null
        while (c.parentId != null) c = categoryById[c.parentId] ?: break
        return c
    }

    companion object { val EMPTY = LedgerData() }
}
