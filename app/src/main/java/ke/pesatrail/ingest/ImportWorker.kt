package ke.pesatrail.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.*
import androidx.room.withTransaction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ke.pesatrail.data.*
import ke.pesatrail.domain.*
import ke.pesatrail.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@EntryPoint @InstallIn(SingletonComponent::class)
interface ImportDependencies { fun repository(): Repository; fun preferences(): Preferences }
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.SMS_ENABLED) return@withContext Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext,ImportDependencies::class.java)
        val repo = deps.repository()
        val scanId = inputData.getString("scan")
        var scan = scanId?.let { repo.dao.scanById(it) } ?: Scan(from=System.currentTimeMillis()-7*86_400_000L,until=System.currentTimeMillis())
        repo.dao.saveScan(scan.copy(status="Scanning"))
        if (ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            repo.dao.saveScan(scan.copy(status="SMS access unavailable. Import a local backup instead.")); return@withContext Result.failure()
        }
        try {
            val selected = deps.preferences().settings.first().providers.split('|').filter { it.isNotBlank() }
            while (!isStopped) {
                ensureActive()
                var batchSize = 0
                applicationContext.contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf("_id","address","body","date","sub_id"),
                    "date >= ? AND date <= ? AND _id > ?",arrayOf(scan.from.toString(),scan.until.toString(),scan.checkpoint.toString()),"_id ASC")?.use { cursor ->
                    while (batchSize < 200 && cursor.moveToNext()) {
                        ensureActive()
                        val sms = Sms(cursor.getString(1).orEmpty(),cursor.getString(2).orEmpty(),cursor.getLong(3),cursor.getString(4) ?: "unknown")
                        val provider = ParserRegistry.parse(sms)?.provider
                        // Commit transaction and checkpoint together so a process restart is safe.
                        repo.db.withTransaction {
                            val outcome = if(selected.isNotEmpty() && provider !in selected) "unsupported" else try { repo.ingest(sms,"SMS") } catch (e: CancellationException) { throw e } catch (_: Exception) {
                                repo.dao.review(Review(fingerprint(sms),sms.sender,sms.body,sms.received,"Import failed; retry available",sms.subscription)); "failed"
                            }
                            scan = scan.copy(checkpoint=cursor.getLong(0),processed=scan.processed+1,imported=scan.imported+if(outcome=="imported")1 else 0,
                                duplicates=scan.duplicates+if(outcome=="duplicate")1 else 0,unsupported=scan.unsupported+if(outcome=="unsupported")1 else 0,
                                failed=scan.failed+if(outcome=="failed")1 else 0,status="Scanning")
                            repo.dao.saveScan(scan)
                        }
                        batchSize++
                    }
                } ?: error("SMS provider unavailable")
                setProgress(workDataOf("processed" to scan.processed))
                if(batchSize < 200) break
                yield()
            }
            repo.dao.saveScan(scan.copy(status=if(isStopped) "Paused; resume available" else "Complete")); Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { repo.dao.saveScan(scan.copy(status="Paused; resume available")) }; throw e
        } catch (_: SecurityException) { repo.dao.saveScan(scan.copy(status="SMS permission revoked. Use backup import.")); Result.failure() }
        catch (_: Exception) { repo.dao.saveScan(scan.copy(status="Scan failed; resume available")); Result.failure() }
    }
}
