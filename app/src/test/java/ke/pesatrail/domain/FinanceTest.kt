package ke.pesatrail.domain

import org.junit.Assert.*
import org.junit.Test
import ke.pesatrail.security.PortableBackup

class FinanceTest {
    private fun parse(sender: String, body: String) = ParserRegistry.parse(Sms(sender,body,1_700_000_000_000))
    @Test fun preciseMinorUnits() { assertEquals(123456L,Money.minor("1,234.56"));assertEquals(10L,Money.minor("0.10"));assertEquals(30L,Money.minor("0.10")+Money.minor("0.20")) }
    @Test fun excessivePrecisionRejected() { assertThrows(ArithmeticException::class.java){Money.minor("1.001")} }
    @Test fun mpesaPaymentWithFeeBalanceAndDate() {
        val p=parse("MPESA","SYN1234567 Confirmed. Ksh1,250.00 sent to SYNTHETIC SHOP 0712345678 on 6/11/26 at 9:30 AM. New M-PESA balance is Ksh5,000.50. Transaction cost, Ksh13.00.")!!
        assertEquals(125000L,p.amount);assertEquals(1300L,p.fee);assertEquals(500050L,p.balance);assertEquals("OUT",p.direction);assertFalse(p.dateInferred);assertEquals("SYN1234567",p.reference)
    }
    @Test fun receivedAndSenderVariation() { val p=parse("M-PESA","SYN1234568 Confirmed. You have received Ksh2,000.00 from SYNTHETIC PERSON on 6/11/26 at 10:00 AM. New M-PESA balance is Ksh7,000.50.")!!;assertEquals("IN",p.direction);assertEquals(200000L,p.amount) }
    @Test fun airtime() { assertEquals("AIRTIME",parse("MPESA","SYN1234569 Confirmed. You bought Ksh100.00 of airtime on 6/11/26 at 10:00 AM.")!!.type) }
    @Test fun withdrawalsAreInternal() { val p=parse("MPESA","SYN1234570 Confirmed. Withdraw Ksh500.00 from agent SYNTHETIC on 6/11/26 at 10:00 AM. Transaction cost, Ksh29.00.")!!;assertEquals("INTERNAL",p.direction);assertEquals("Cash withdrawal",p.category) }
    @Test fun bankFormats() { listOf("EQUITY","KCB","CO-OPBANK","NCBA","ABSA","STANCHART","FAMILYBANK","I&MBANK","TESTSACCO","AIRTELMONEY").forEach { sender -> assertNotNull(sender,parse(sender,"Your account 1234 has been credited with KES 1,200.50 from SYNTHETIC PERSON. Ref SYNTHETIC1. Available balance KES 5,000.00.")) } }
    @Test fun bankDebit() { assertEquals("OUT",parse("KCB","Your account 1234 has been debited KES 900.00 for purchase at SYNTHETIC. Ref SYNTHETIC2.")!!.direction) }
    @Test fun otpLoginAndOffersRejected() { listOf("OTP 123456 for KES 500.00 purchase","Your password for sent KES 500 is 123456","Login alert: received KES 500","You are eligible for a loan. Receive KES 500.00","Transaction failed. KES 500.00 sent to X","Verification code 123456 for KES 20.00 paid").forEach{assertNull(it,parse("MPESA",it))} }
    @Test fun unknownSendersRejected() { assertNull(parse("STRANGER","Received KES 500.00 from X")) }
    @Test fun missingAmountRejected() { assertNull(parse("KCB","Your transfer was received")) }
    @Test fun refundsDoNotInflateIncome() { val p=parse("MPESA","SYN1234571 Confirmed. Ksh100.00 reversed from SYNTHETIC SHOP.")!!;assertEquals("REFUND",p.type);assertEquals("IN",p.direction);val t=totals(listOf(Entry(10000,0,"OUT","PAYMENT",false),Entry(p.amount,0,p.direction,p.type,false)));assertEquals(0,t.income);assertEquals(0,t.expenses) }
    @Test fun feesCountOnceAndTransfersExcluded() { val t=totals(listOf(Entry(10000,200,"OUT","PAYMENT",false),Entry(50000,500,"INTERNAL","WITHDRAWAL",false),Entry(100000,0,"INTERNAL","TRANSFER",false),Entry(1000,0,"OUT","CHARGE",false)));assertEquals(1700,t.fees);assertEquals(11700,t.expenses);assertEquals(-11700,t.net) }
    @Test fun loansAreLiabilities() { val t=totals(listOf(Entry(50000,0,"IN","LOAN",false)));assertEquals(0,t.income) }
    @Test fun salaryDepositIsIncome() { val p=parse("EQUITY","KES 25000.00 deposited into your account 1234 from SYNTHETIC EMPLOYER.")!!;assertEquals("IN",p.direction);assertEquals("RECEIVED",p.type) }
    @Test fun paybillDestinationIsNotTheWallet() { val p=parse("MPESA","SYN0000099 Confirmed. Ksh100.00 paid to SYNTHETIC POWER for account 123456 on 6/11/26 at 9:00 AM.")!!;assertFalse(p.account.contains("123456")) }
    @Test fun fulizaUsageNotIncome() { val p=parse("MPESA","SYN0000098 Confirmed. You have used Ksh100.00 of Fuliza M-PESA.")!!;assertEquals("LOAN",p.type);assertEquals("IN",p.direction);assertEquals(0L,totals(listOf(Entry(p.amount,0,p.direction,p.type,false))).income) }
    @Test fun mixedLoanSettlementRequiresReview() { assertNull(parse("MPESA","SYN0000097 Confirmed. You have received Ksh1000.00. Ksh200.00 used to repay your Fuliza loan.")) }
    @Test fun excludedRecordsDoNotCount() { assertEquals(0,totals(listOf(Entry(1000,10,"OUT","PAYMENT",true))).expenses) }
    @Test fun fingerprintNormalizesWhitespace() { assertEquals(fingerprint(Sms("MPESA","  paid  KES 10  ",1)),fingerprint(Sms("mpesa","paid KES 10",1))) }
    @Test fun identicalTextOnDifferentDaysIsNotLost() { assertNotEquals(fingerprint(Sms("KCB","credited KES 10",1)),fingerprint(Sms("KCB","credited KES 10",2))) }
    @Test fun differentSimIsNotSilentlyMerged() { assertNotEquals(fingerprint(Sms("MPESA","paid KES 10",1,"1")),fingerprint(Sms("MPESA","paid KES 10",1,"2"))) }
    @Test fun absentDetailsRemainUnknown() { val p=parse("KCB","Your account has been credited KES 100.00.")!!;assertNull(p.balance);assertNull(p.fee);assertNull(p.reference);assertTrue(p.dateInferred) }
    @Test fun categorization() { assertEquals("Food and groceries",Categories.infer("NAIVAS","PAYMENT"));assertEquals("Internet and mobile data",Categories.infer("FAIBA","PAYMENT")) }
    @Test fun backupRoundTripAndWrongPassword() {
        val plaintext="Synthetic financial history".toByteArray();val encrypted=PortableBackup.encrypt(plaintext,"synthetic-passphrase".toCharArray())
        assertArrayEquals(plaintext,PortableBackup.decrypt(encrypted,"synthetic-passphrase".toCharArray()))
        assertThrows(Exception::class.java){PortableBackup.decrypt(encrypted,"wrong-passphrase".toCharArray())}
    }
    @Test fun backupTamperingRejected() { val encrypted=PortableBackup.encrypt("test".toByteArray(),"synthetic-passphrase".toCharArray());encrypted[encrypted.lastIndex]=(encrypted.last().toInt() xor 1).toByte();assertThrows(Exception::class.java){PortableBackup.decrypt(encrypted,"synthetic-passphrase".toCharArray())} }
    @Test fun backupsUseRandomSaltAndNonce() { assertFalse(PortableBackup.encrypt("test".toByteArray(),"synthetic-passphrase".toCharArray()).contentEquals(PortableBackup.encrypt("test".toByteArray(),"synthetic-passphrase".toCharArray()))) }
}
