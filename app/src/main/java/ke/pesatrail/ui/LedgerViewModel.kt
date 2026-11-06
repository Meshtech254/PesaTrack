package ke.pesatrail.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.pesatrail.data.*
import ke.pesatrail.domain.*
import ke.pesatrail.ingest.*
import ke.pesatrail.security.PortableBackup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltViewModel class LedgerViewModel @Inject constructor(app: Application,val repo: Repository,val preferences: Preferences,val backup: BackupService) : AndroidViewModel(app) {
    val transactions = repo.dao.observeTransactions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts = repo.dao.accounts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = repo.dao.reviews().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = repo.dao.budgets().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = repo.dao.goals().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    val scan = repo.dao.scan().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val settings = preferences.settings.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val notice = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val fileProgress = MutableStateFlow("")
    private var fileJob: Job? = null
    fun task(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { block() } catch(e: CancellationException) { throw e } catch (_: Exception) { notice.value="Operation failed. Check the selected file or values and try again." } } }
    fun bool(key: String,value: Boolean) = task { preferences.bool(key,value) }
    fun text(key: String,value: String) = task { preferences.text(key,value) }
    fun scan(from: Long, until: Long, resume: Boolean = false) = task {
        require(from <= until)
        val s = if(resume) scan.value ?: Scan(from=from,until=until) else Scan(from=from,until=until)
        repo.dao.saveScan(s.copy(status="Queued"))
        WorkManager.getInstance(getApplication()).enqueueUniqueWork("history",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf("scan" to s.id)).build())
    }
    fun cancel() { WorkManager.getInstance(getApplication()).cancelUniqueWork("history"); fileJob?.cancel() }
    fun reconciliation(enabled: Boolean) = task {
        preferences.bool("reconcile",enabled)
        val wm = WorkManager.getInstance(getApplication())
        if(enabled) wm.enqueueUniquePeriodicWork("periodic-sms",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ImportWorker>(12,TimeUnit.HOURS).build()) else wm.cancelUniqueWork("periodic-sms")
    }
    fun importXml(uri: Uri, from: Long, until: Long) {
        if(busy.value) return
        fileJob = viewModelScope.launch(Dispatchers.IO) {
            busy.value=true; var s = Scan(from=from,until=until,status="Importing XML")
            try {
                repo.dao.saveScan(s)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    SmsBackupReader.read(stream) { sms ->
                        val result = if(sms.received in from..until) repo.ingest(sms,"XML") else "unsupported"
                        s=s.copy(processed=s.processed+1,imported=s.imported+if(result=="imported")1 else 0,duplicates=s.duplicates+if(result=="duplicate")1 else 0,unsupported=s.unsupported+if(result=="unsupported")1 else 0)
                        fileProgress.value="${s.processed} processed · ${s.imported} imported"
                        if(s.processed % 100 == 0) repo.dao.saveScan(s)
                    }
                } ?: error("Cannot read file")
                repo.dao.saveScan(s.copy(status="Complete"))
            } catch(e: CancellationException) { withContext(NonCancellable) { repo.dao.saveScan(s.copy(status="Cancelled. Reimport safely to resume.")) }; throw e }
            catch (_: Exception) { repo.dao.saveScan(s.copy(status="File import stopped: invalid or inaccessible XML; reimport safely after correction",failed=s.failed+1)) }
            finally { busy.value=false }
        }
    }
    fun retryReviews() = task { repo.dao.allReviews().forEach { repo.ingest(Sms(it.sender,it.body,it.received,it.subscription),"RETRY") }; notice.value="Retry complete; unmatched messages remain available for review." }
    fun edit(tx: Transaction) = task { repo.edit(tx) }
    fun delete(tx: Transaction) = task { repo.dao.delete(tx) }
    fun manual(account: String, party: String, amount: String, direction: String, category: String, date: Long) = task {
        val value=Money.minor(amount); require(value>0)
        val id=UUID.randomUUID().toString()
        repo.dao.addAccount(Account(account,account,"Manual","Cash wallet"))
        repo.dao.insert(Transaction(id=id,sender="Manual",provider="Manual",account=account,merchant=party,type="MANUAL",amount=value,direction=direction,occurred=date,received=date,category=category,importMethod="MANUAL",fingerprint=id))
    }
    fun savePin(pin: String) = task {
        require(pin.matches(Regex("\\d{6,12}")))
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        preferences.text("salt",Base64.getEncoder().encodeToString(salt))
        preferences.text("pinHash",Base64.getEncoder().encodeToString(PortableBackup.pinHash(pin,salt)))
        notice.value="App PIN enabled. Auto-lock activates after one minute away."
    }
    suspend fun checkPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val s=settings.value ?: return@withContext false
        runCatching { MessageDigest.isEqual(Base64.getDecoder().decode(s.pinHash),PortableBackup.pinHash(pin,Base64.getDecoder().decode(s.salt))) }.getOrDefault(false)
    }
    fun export(uri: Uri, records: List<Transaction>) = task {
        getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(backup.csv(records)) }; notice.value="Selected transactions exported. CSV is unencrypted."
    }
    fun createBackup(uri: Uri, password: String) = task {
        val bytes=backup.create(password.toCharArray())
        getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }; bytes.fill(0); notice.value="Encrypted backup saved. Keep the passphrase separately."
    }
    fun restoreBackup(uri: Uri, password: String) = task {
        val bytes=getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readNBytes(52_428_801) }
        backup.restore(bytes,password.toCharArray()); bytes.fill(0); notice.value="Backup restored; duplicate records skipped."
    }
}
