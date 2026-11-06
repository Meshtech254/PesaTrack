package ke.pesatrail.data

import androidx.room.withTransaction
import com.google.gson.Gson
import ke.pesatrail.security.PortableBackup
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class BackupSnapshot(val version: Int = 1, val transactions: List<Transaction>, val accounts: List<Account>, val reviews: List<Review>, val rules: List<LocalRule>, val mappings: List<MerchantCategory>, val budgets: List<Budget>, val goals: List<Goal>)
class BackupService @Inject constructor(private val repo: Repository) {
    suspend fun create(password: CharArray): ByteArray {
        val snapshot = repo.db.withTransaction { val d = repo.dao
            BackupSnapshot(transactions=d.all(),accounts=d.allAccounts(),reviews=d.allReviews(),rules=d.rules(),mappings=d.allMappings(),budgets=d.budgets().first(),goals=d.goals().first()) }
        val plaintext = Gson().toJson(snapshot).toByteArray()
        return try { PortableBackup.encrypt(plaintext,password) } finally { plaintext.fill(0); password.fill('\u0000') }
    }
    suspend fun restore(bytes: ByteArray, password: CharArray) {
        val plaintext = try { PortableBackup.decrypt(bytes,password) } finally { password.fill('\u0000') }
        val snapshot = try { Gson().fromJson(String(plaintext),BackupSnapshot::class.java) } finally { plaintext.fill(0) }
        require(snapshot.version == 1 && snapshot.transactions.size <= 500_000) { "Unsupported backup" }
        require(snapshot.transactions.all { it.amount > 0 && (it.fee ?: 0) >= 0 && it.direction in listOf("IN","OUT","INTERNAL") && it.currency == "KES" }) { "Invalid transaction data" }
        repo.db.withTransaction {
            val d = repo.dao
            snapshot.accounts.forEach { d.addAccount(it) }; snapshot.transactions.forEach { d.insert(it) }
            snapshot.reviews.forEach { d.review(it) }; snapshot.rules.forEach { d.saveRule(it) }; snapshot.mappings.forEach { d.saveMapping(it) }
            snapshot.budgets.forEach { d.saveBudget(it) }; snapshot.goals.forEach { d.saveGoal(it) }
        }
    }
    fun csv(records: List<Transaction>): String {
        fun cell(value: Any?): String {
            var s = value?.toString().orEmpty()
            if(s.firstOrNull() in listOf('=','+','-','@','\t','\r')) s = "'"+s
            return "\"" + s.replace("\"","\"\"") + "\""
        }
        return "Date,Provider,Account,Reference,Party,Direction,Type,Amount minor units,Fee minor units,Category,Notes\r\n" + records.joinToString("\r\n") { t ->
            listOf(java.time.Instant.ofEpochMilli(t.occurred),t.provider,t.account,t.reference,t.party,t.direction,t.type,t.amount,t.fee,t.category,t.notes).joinToString(",",transform=::cell)
        }
    }
}
