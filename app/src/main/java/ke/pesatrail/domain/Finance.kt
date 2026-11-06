package ke.pesatrail.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object Money {
    fun minor(text: String): Long = BigDecimal(text.replace(",", "").trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    fun format(value: Long): String = "KES " + String.format(Locale.US, "%,.2f", BigDecimal.valueOf(value, 2))
}
data class Sms(val sender: String, val body: String, val received: Long, val subscription: String = "unknown")
data class Parsed(
    val reference: String?, val provider: String, val account: String, val amount: Long,
    val direction: String, val type: String, val fee: Long?, val balance: Long?,
    val party: String?, val identifier: String?, val occurred: Long, val dateInferred: Boolean,
    val category: String, val confidence: Int, val fingerprint: String
)
object Categories {
    val all = listOf("Food and groceries", "Rent and housing", "Transport", "Internet and mobile data", "Airtime", "Shopping", "Utilities", "Entertainment", "Health", "Education", "Business", "Savings", "Investments", "Loan payments", "Transfers", "Transaction fees", "Cash withdrawal", "Other")
    fun infer(text: String, type: String): String = when {
        type == "WITHDRAWAL" -> "Cash withdrawal"
        type in listOf("TRANSFER", "DEPOSIT") -> "Transfers"
        type == "AIRTIME" -> "Airtime"
        type == "LOAN_REPAYMENT" -> "Loan payments"
        type == "CHARGE" -> "Transaction fees"
        Regex("supermarket|naivas|carrefour|quickmart|grocery|restaurant|cafe", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Food and groceries"
        Regex("internet|zuku|safaricom home|faiba", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Internet and mobile data"
        Regex("kenya power|kplc|water", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Utilities"
        else -> "Other"
    }
}
interface ProviderParser { fun recognizes(sender: String): Boolean; fun parse(sms: Sms): Parsed? }
class RuleParser(private val provider: String, private val senders: Regex) : ProviderParser {
    override fun recognizes(sender: String) = senders.containsMatchIn(sender.replace(" ", ""))
    override fun parse(sms: Sms): Parsed? {
        val body = sms.body
        if (Regex("\\b(otp|one.time|verification|password|login|sign.in|security code|verification code)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) return null
        val incoming = Regex("received|credited|deposited|refund|reversed|reversal|disbursed", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val outgoing = Regex("sent|paid|debited|withdraw|bought|purchase|repayment|repaid|charged|transferred", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (!incoming && !outgoing) return null
        val amountPattern = Regex("(?:Ksh\\.?|KES|KShs\\.?|Kshs\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        // Reject offers and notices; a completed transaction verb must be near the first amount.
        if (Regex("eligible|apply for|offer|could receive|will be|failed|unsuccessful|declined|insufficient|request to", RegexOption.IGNORE_CASE).containsMatchIn(body)) return null
        val first = amountPattern.find(body) ?: return null
        val amount = runCatching { Money.minor(first.groupValues[1]) }.getOrNull() ?: return null
        if (amount <= 0) return null
        fun amountAfter(label: String): Long? = Regex("(?:$label)\\s*(?:is|of|:)?\\s*(?:Ksh\\.?|KES|Kshs\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.let { runCatching { Money.minor(it) }.getOrNull() }
        val type = when {
            Regex("refund|reversal|reversed", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "REFUND"
            Regex("repayment|repaid", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "LOAN_REPAYMENT"
            Regex("disbursed|loan of|fuliza.*(?:used|covered)", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "LOAN"
            Regex("withdraw", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "WITHDRAWAL"
            Regex("airtime", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "AIRTIME"
            Regex("deposited|cash deposit", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "DEPOSIT"
            Regex("charged|interest charge", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "CHARGE"
            incoming -> "RECEIVED"
            else -> "PAYMENT"
        }
        val reference = Regex("^\\s*([A-Z0-9]{8,16})\\s+(?:Confirmed|confirmed)").find(body)?.groupValues?.get(1)
            ?: Regex("(?:ref(?:erence)?|transaction\\s*(?:id|no|code))\\s*[:#.]?\\s*([A-Z0-9-]{5,30})", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
        val accountId = Regex("(?:account|a/c|acc)\\s*[:#.]?\\s*([*xX0-9-]{3,24})", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
        val party = Regex("(?:sent to|paid to|received from|from|to)\\s+(.+?)(?=\\s+on\\s|\\s+at\\s|\\. |\\s+(?:New|Your|Balance|Transaction)\\b|$)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()?.take(120)
        val dateMatch = Regex("\\bon (\\d{1,2}/\\d{1,2}/\\d{2,4}) at (\\d{1,2}:\\d{2}\\s*[AP]M)", RegexOption.IGNORE_CASE).find(body)
        val date = dateMatch?.let { m -> runCatching {
            val d = m.groupValues[1].split('/'); val year = d[2].toInt().let { if (it < 100) 2000 + it else it }
            val time = LocalTime.parse(m.groupValues[2].replace(" ", "").uppercase(), DateTimeFormatter.ofPattern("h:mma", Locale.US))
            LocalDate.of(year, d[1].toInt(), d[0].toInt()).atTime(time).atZone(ZoneId.of("Africa/Nairobi")).toInstant().toEpochMilli()
        }.getOrNull() }
        return Parsed(reference, provider, "$provider · ${accountId ?: "unidentified / SIM ${sms.subscription}"}", amount,
            if (type in listOf("WITHDRAWAL", "DEPOSIT")) "INTERNAL" else if (incoming) "IN" else "OUT", type,
            amountAfter("transaction cost,?|transaction fee|charges?|fee"), amountAfter("(?:new )?(?:M-PESA |account |available )?balance(?: is)?"),
            party, party?.let { Regex("(?:254|0)[17]\\d{8}").find(it)?.value }, date ?: sms.received, date == null,
            Categories.infer(party.orEmpty() + " " + body, type), if (reference != null) 90 else 65,
            fingerprint(sms))
    }
}
fun fingerprint(sms: Sms): String = MessageDigest.getInstance("SHA-256").digest((sms.sender.trim().uppercase() + "|" + sms.subscription + "|" + sms.body.trim().replace(Regex("\\s+"), " ")).toByteArray()).joinToString("") { "%02x".format(it) }
object ParserRegistry {
    val providers = linkedMapOf("M-Pesa" to "MPESA|M-PESA|SAFARICOM", "Airtel Money" to "AIRTEL|AIRTELMONEY", "Equity" to "EQUITY|EQUITEL", "KCB" to "KCB", "Co-operative Bank" to "CO.?OP|COOPERATIVE", "NCBA" to "NCBA|CBA", "Absa" to "ABSA|BARCLAYS", "Standard Chartered" to "SCBANK|STANCHART|STANDARDCHARTERED", "Family Bank" to "FAMILY", "I&M Bank" to "I&M|IMBANK", "SACCO" to "SACCO")
    private val parsers = providers.map { (name, pattern) -> RuleParser(name, Regex(pattern, RegexOption.IGNORE_CASE)) }
    fun recognized(sender: String) = parsers.any { it.recognizes(sender) }
    fun parse(sms: Sms): Parsed? = parsers.firstOrNull { it.recognizes(sms.sender) }?.parse(sms)
}
data class Totals(val income: Long, val expenses: Long, val fees: Long, val refunds: Long) { val net get() = income - expenses }
data class Entry(val amount: Long, val fee: Long, val direction: String, val type: String, val excluded: Boolean)
fun totals(entries: List<Entry>): Totals {
    val active = entries.filterNot { it.excluded }
    val fees = active.sumOf { if (it.type == "CHARGE") it.amount else it.fee }
    val refunds = active.filter { it.type == "REFUND" }.sumOf { it.amount }
    val income = active.filter { it.direction == "IN" && it.type !in listOf("REFUND", "LOAN", "DEPOSIT") }.sumOf { it.amount }
    val spending = active.filter { it.direction == "OUT" && it.type != "CHARGE" }.sumOf { it.amount }
    return Totals(income, spending + fees - refunds, fees, refunds)
}
