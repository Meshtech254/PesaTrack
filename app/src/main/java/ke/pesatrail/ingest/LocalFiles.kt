package ke.pesatrail.ingest

import android.util.Xml
import ke.pesatrail.domain.Sms
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object SmsBackupReader {
    // SMS Backup & Restore XML. No contacts, storage permission, or network needed.
    suspend fun read(stream: InputStream, consume: suspend (Sms) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false)
        parser.setInput(stream, "UTF-8")
        var count = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            currentCoroutineContext().ensureActive()
            require(parser.eventType != XmlPullParser.DOCDECL) { "XML document declarations are not supported" }
            if(parser.eventType == XmlPullParser.START_TAG && parser.name == "sms") {
                require(++count <= 500_000) { "Backup exceeds 500,000 messages" }
                val body = parser.getAttributeValue(null,"body") ?: error("SMS missing body")
                require(body.length <= 32_768) { "SMS too long" }
                val date = parser.getAttributeValue(null,"date")?.toLongOrNull() ?: error("SMS missing valid received date")
                consume(Sms(parser.getAttributeValue(null,"address") ?: "Unknown",body,date,parser.getAttributeValue(null,"sub_id") ?: "unknown"))
            }
            parser.nextToken()
        }
    }
}
