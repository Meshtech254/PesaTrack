package ke.pesatrail.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.EntryPointAccessors
import ke.pesatrail.domain.Sms
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if(parts.isNotEmpty()) {
                        val sms = Sms(parts.first().originatingAddress.orEmpty(),parts.joinToString(""){it.messageBody.orEmpty()},parts.first().timestampMillis,intent.getIntExtra("subscription",-1).toString())
                        EntryPointAccessors.fromApplication(context.applicationContext,ImportDependencies::class.java).repository().ingest(sms,"RECEIVED_SMS")
                    }
                }
            } catch (_: Exception) {
                // Reconciliation reads the ContentProvider when permitted; never log SMS bodies.
                WorkManagerCompat.reconcile(context)
            } finally { pending.finish() }
        }
    }
}
object WorkManagerCompat {
    fun reconcile(context: Context) { androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("reconcile",androidx.work.ExistingWorkPolicy.KEEP,androidx.work.OneTimeWorkRequestBuilder<ImportWorker>().build()) }
}
