package ke.pesatrail.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ke.pesatrail.domain.Entry
import java.util.UUID

@Entity(tableName = "transactions", indices = [Index(value=["fingerprint"], unique=true), Index(value=["externalKey"], unique=true), Index(value=["occurred"]), Index(value=["account"])])
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val reference: String? = null, val externalKey: String? = null,
    val sender: String, val provider: String, val account: String, val sourceName: String? = null,
    val sourceIdentifier: String? = null, val destinationName: String? = null, val destinationIdentifier: String? = null,
    val merchant: String? = null, val type: String, val amount: Long, val currency: String = "KES", val fee: Long? = null,
    val direction: String, val occurred: Long, val received: Long, val balance: Long? = null,
    val category: String, val description: String = "", val original: String = "", val confidence: Int = 100,
    val importMethod: String, val imported: Long = System.currentTimeMillis(), val modified: Boolean = false,
    val fingerprint: String, val excluded: Boolean = false, val dateInferred: Boolean = false, val notes: String = ""
) { fun entry() = Entry(amount, fee ?: 0, direction, type, excluded); val party get() = merchant ?: destinationName ?: sourceName ?: description.ifBlank { type.lowercase().replace('_',' ') } }
@Entity(tableName="accounts")
data class Account(@PrimaryKey val id: String, val name: String, val provider: String, val kind: String = "Other financial account", val archived: Boolean = false)
@Entity(tableName="review")
data class Review(@PrimaryKey val fingerprint: String, val sender: String, val body: String, val received: Long, val reason: String, val subscription: String = "unknown")
@Entity(tableName="rules")
data class LocalRule(@PrimaryKey val id: String = UUID.randomUUID().toString(), val sender: String, val contains: String, val direction: String, val category: String)
@Entity(tableName="mappings")
data class MerchantCategory(@PrimaryKey val merchant: String, val category: String)
@Entity(tableName="budgets")
data class Budget(@PrimaryKey val category: String, val amount: Long, val weekly: Boolean = false)
@Entity(tableName="goals")
data class Goal(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val target: Long, val targetDate: String, val allocated: Long = 0)
@Entity(tableName="scans")
data class Scan(@PrimaryKey val id: String = UUID.randomUUID().toString(), val started: Long = System.currentTimeMillis(), val from: Long, val until: Long, val checkpoint: Long = -1, val processed: Int = 0, val imported: Int = 0, val duplicates: Int = 0, val unsupported: Int = 0, val failed: Int = 0, val status: String = "Queued")

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurred DESC") fun observeTransactions(): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions ORDER BY occurred DESC") suspend fun all(): List<Transaction>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(tx: Transaction): Long
    @Update suspend fun update(tx: Transaction)
    @Delete suspend fun delete(tx: Transaction)
    @Query("DELETE FROM transactions WHERE importMethod != 'MANUAL'") suspend fun clearImported()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun addAccount(account: Account)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAccount(account: Account)
    @Query("SELECT * FROM accounts") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>
    @Query("UPDATE transactions SET account = :target WHERE account = :source") suspend fun moveAccount(source: String, target: String)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun removeAccount(id: String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun review(item: Review)
    @Query("SELECT * FROM review ORDER BY received DESC") fun reviews(): Flow<List<Review>>
    @Query("SELECT * FROM review") suspend fun allReviews(): List<Review>
    @Query("DELETE FROM review WHERE fingerprint = :id") suspend fun removeReview(id: String)
    @Query("DELETE FROM review") suspend fun clearReviews()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveMapping(mapping: MerchantCategory)
    @Query("SELECT category FROM mappings WHERE merchant = :merchant") suspend fun category(merchant: String): String?
    @Query("SELECT * FROM mappings") suspend fun allMappings(): List<MerchantCategory>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRule(rule: LocalRule)
    @Query("SELECT * FROM rules") suspend fun rules(): List<LocalRule>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveBudget(budget: Budget)
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
    @Delete suspend fun deleteBudget(budget: Budget)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveGoal(goal: Goal)
    @Query("SELECT * FROM goals") fun goals(): Flow<List<Goal>>
    @Delete suspend fun deleteGoal(goal: Goal)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveScan(scan: Scan)
    @Query("SELECT * FROM scans ORDER BY started DESC LIMIT 1") fun scan(): Flow<Scan?>
    @Query("SELECT * FROM scans WHERE id = :id") suspend fun scanById(id: String): Scan?
}
@Database(entities=[Transaction::class, Account::class, Review::class, LocalRule::class, MerchantCategory::class, Budget::class, Goal::class, Scan::class], version=1, exportSchema=true)
abstract class LedgerDatabase : RoomDatabase() { abstract fun ledger(): LedgerDao }
