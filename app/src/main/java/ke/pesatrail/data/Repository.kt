package ke.pesatrail.data

import androidx.room.withTransaction
import ke.pesatrail.domain.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class Repository @Inject constructor(val db: LedgerDatabase, val dao: LedgerDao) {
    suspend fun ingest(sms: Sms, method: String): String = db.withTransaction {
        val rule = dao.rules().firstOrNull { sms.sender.equals(it.sender,true) && sms.body.contains(it.contains,true) }
        val parsed = ParserRegistry.parse(sms) ?: rule?.let {
            // Local rules only authorize a sender; the same OTP/notice guards still apply.
            RuleParser(sms.sender, Regex(Regex.escape(sms.sender),RegexOption.IGNORE_CASE)).parse(sms)
                ?.copy(direction=it.direction, category=it.category, confidence=50)
        }
        if (parsed == null) {
            if ((ParserRegistry.recognized(sms.sender) || rule != null) && !Regex("otp|password|verification|login",RegexOption.IGNORE_CASE).containsMatchIn(sms.body)) {
                dao.review(Review(fingerprint(sms),sms.sender,sms.body,sms.received,"Unrecognized format; no transaction created",sms.subscription))
            }
            return@withTransaction "unsupported"
        }
        val p = parsed
        val mapped = p.party?.lowercase(Locale.ROOT)?.let { dao.category(it) }
        val tx = Transaction(reference=p.reference, externalKey=p.reference?.let { "${p.provider}|${p.account}|${p.type}|${it.uppercase()}" },
            sender=sms.sender, provider=p.provider, account=p.account,
            sourceName=if(p.direction=="IN") p.party else null, sourceIdentifier=if(p.direction=="IN") p.identifier else null,
            destinationName=if(p.direction!="IN") p.party else null, destinationIdentifier=if(p.direction!="IN") p.identifier else null,
            merchant=if(p.type=="PAYMENT") p.party else null, type=p.type, amount=p.amount, fee=p.fee,
            direction=p.direction, occurred=p.occurred, received=sms.received, balance=p.balance,
            category=mapped ?: p.category, original=sms.body, confidence=p.confidence, importMethod=method,
            fingerprint=p.fingerprint, dateInferred=p.dateInferred)
        val inserted = dao.insert(tx)
        dao.removeReview(p.fingerprint)
        if (inserted == -1L) "duplicate" else {
            dao.addAccount(Account(p.account,p.account,p.provider,if(p.provider.contains("Money") || p.provider=="M-Pesa") "Mobile wallet" else "Bank account"))
            "imported"
        }
    }
    suspend fun edit(tx: Transaction, remember: Boolean = true) = db.withTransaction {
        dao.update(tx.copy(modified=true))
        if (remember && tx.party.isNotBlank()) dao.saveMapping(MerchantCategory(tx.party.lowercase(Locale.ROOT),tx.category))
    }
    suspend fun merge(source: String, target: String) = db.withTransaction { require(source != target); dao.moveAccount(source,target); dao.removeAccount(source) }
}
