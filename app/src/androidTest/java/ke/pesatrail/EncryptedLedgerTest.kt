package ke.pesatrail

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.room.withTransaction
import ke.pesatrail.data.*
import ke.pesatrail.domain.*
import ke.pesatrail.ingest.SmsBackupReader
import ke.pesatrail.security.Vault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedLedgerTest {
    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var repo: Repository
    private val key=ByteArray(32){(it+1).toByte()}
    @Before fun setup() {
        context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        context.deleteDatabase("integration.db")
        db=open();repo=Repository(db,db.ledger())
    }
    private fun open()=Room.databaseBuilder(context,LedgerDatabase::class.java,"integration.db").openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
    @After fun cleanup(){db.close();context.deleteDatabase("integration.db")}
    private fun sms(id: Int)=Sms("MPESA","SYN${id.toString().padStart(7,'0')} Confirmed. Ksh125.00 paid to SYNTHETIC SHOP on 6/11/26 at 9:30 AM. New M-PESA balance is Ksh500.00.",1_793_942_000_000)
    @Test fun encryptedPersistenceAndDeduplication()=runBlocking {
        assertEquals("imported",repo.ingest(sms(1),"TEST"));assertEquals("duplicate",repo.ingest(sms(1),"TEST"))
        db.close();db=open();repo=Repository(db,db.ledger())
        assertEquals(1,db.ledger().all().size)
        assertFalse(context.getDatabasePath("integration.db").readBytes().take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
    @Test fun providerReferenceDedupAcrossMinorTextChanges()=runBlocking {
        assertEquals("imported",repo.ingest(sms(2),"TEST"))
        assertEquals("duplicate",repo.ingest(sms(2).copy(body=sms(2).body+" Keep safe."),"XML"))
    }
    @Test fun rollbackKeepsCheckpointAndTransactionTogether()=runBlocking {
        val scan=Scan(from=0,until=Long.MAX_VALUE);db.ledger().saveScan(scan)
        runCatching { db.withTransaction { repo.ingest(sms(3),"TEST");db.ledger().saveScan(scan.copy(checkpoint=3,processed=1));error("Synthetic interruption") } }
        assertEquals(0,db.ledger().all().size);assertEquals(-1L,db.ledger().scanById(scan.id)!!.checkpoint)
    }
    @Test fun xmlBatchAndCancellationSafeReimport()=runBlocking {
        val xml="<smses>"+(1..250).joinToString(""){"<sms address=\"MPESA\" date=\"1793942000000\" body=\"${sms(it).body}\"/>"}+"</smses>"
        var count=0
        try { SmsBackupReader.read(xml.byteInputStream()){repo.ingest(it,"XML");if(++count==101)throw CancellationException("Synthetic stop")} }catch(_:CancellationException){}
        assertEquals(101,db.ledger().all().size)
        SmsBackupReader.read(xml.byteInputStream()){repo.ingest(it,"XML")}
        assertEquals(250,db.ledger().all().size)
    }
    @Test fun backupRestoresRecordsAndCategories()=runBlocking {
        repo.ingest(sms(10),"TEST");val original=db.ledger().all().single();repo.edit(original.copy(category="Education",notes="Synthetic note"))
        val service=BackupService(repo);val bytes=service.create("synthetic-passphrase".toCharArray())
        db.ledger().clearTransactions();service.restore(bytes,"synthetic-passphrase".toCharArray())
        assertEquals("Education",db.ledger().all().single().category);assertEquals("Synthetic note",db.ledger().all().single().notes)
    }
    @Test fun manualEditsSurviveRescan()=runBlocking {
        repo.ingest(sms(11),"TEST");repo.edit(db.ledger().all().single().copy(category="Rent and housing",direction="INTERNAL"))
        repo.ingest(sms(11),"TEST");assertEquals("INTERNAL",db.ledger().all().single().direction)
    }
    @Test fun keystoreWrappingStable(){assertArrayEquals(Vault.databaseKey(context),Vault.databaseKey(context))}
    @Test fun malformedXmlIsReported()=runBlocking { var failed=false;try{SmsBackupReader.read("<smses><sms address=\"MPESA\" body=\"Invalid\"/></smses>".byteInputStream()) {}}catch(_:Exception){failed=true};assertTrue(failed) }
}
