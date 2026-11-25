package ke.pesatrail.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small native vector set; avoids shipping a complete icon catalogue. */
object AppIcons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.()->Unit)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=draw)
    }.build()
    val Home=icon("Home"){moveTo(3f,10f);lineTo(12f,3f);lineTo(21f,10f);verticalLineTo(21f);horizontalLineTo(15f);verticalLineTo(14f);horizontalLineTo(9f);verticalLineTo(21f);horizontalLineTo(3f);close()}
    val ReceiptLong=icon("Transactions"){moveTo(5f,3f);lineTo(8f,5f);lineTo(12f,3f);lineTo(16f,5f);lineTo(19f,3f);verticalLineTo(21f);lineTo(15f,19f);lineTo(12f,21f);lineTo(8f,19f);lineTo(5f,21f);close();moveTo(8f,9f);horizontalLineTo(16f);moveTo(8f,13f);horizontalLineTo(16f)}
    val AccountBalanceWallet=icon("Accounts"){moveTo(3f,6f);horizontalLineTo(21f);verticalLineTo(20f);horizontalLineTo(3f);close();moveTo(3f,6f);lineTo(18f,3f);verticalLineTo(6f);moveTo(16f,11f);horizontalLineTo(21f);moveTo(16f,15f);horizontalLineTo(21f)}
    val BarChart=icon("Analytics"){moveTo(4f,20f);horizontalLineTo(21f);moveTo(6f,16f);verticalLineTo(11f);moveTo(12f,16f);verticalLineTo(4f);moveTo(18f,16f);verticalLineTo(8f)}
    val Settings=icon("Settings"){moveTo(4f,6f);horizontalLineTo(20f);moveTo(4f,12f);horizontalLineTo(20f);moveTo(4f,18f);horizontalLineTo(20f);moveTo(8f,3f);verticalLineTo(9f);moveTo(16f,9f);verticalLineTo(15f);moveTo(10f,15f);verticalLineTo(21f)}
    val Lock=icon("Lock"){moveTo(5f,10f);horizontalLineTo(19f);verticalLineTo(21f);horizontalLineTo(5f);close();moveTo(8f,10f);verticalLineTo(7f);curveTo(8f,1f,16f,1f,16f,7f);verticalLineTo(10f);moveTo(12f,14f);verticalLineTo(17f)}
    val Sms=icon("SMS"){moveTo(3f,4f);horizontalLineTo(21f);verticalLineTo(17f);horizontalLineTo(8f);lineTo(3f,21f);close();moveTo(7f,9f);horizontalLineTo(17f);moveTo(7f,13f);horizontalLineTo(14f)}
    val ArrowBack=icon("Back"){moveTo(20f,12f);horizontalLineTo(4f);lineTo(10f,6f);moveTo(4f,12f);lineTo(10f,18f)}
    val Add=icon("Add"){moveTo(12f,4f);verticalLineTo(20f);moveTo(4f,12f);horizontalLineTo(20f)}
    val ExpandMore=icon("Expand"){moveTo(6f,9f);lineTo(12f,15f);lineTo(18f,9f)}
    val NorthEast=icon("Outgoing"){moveTo(5f,19f);lineTo(19f,5f);moveTo(8f,5f);horizontalLineTo(19f);verticalLineTo(16f)}
    val SouthWest=icon("Incoming"){moveTo(19f,5f);lineTo(5f,19f);moveTo(5f,8f);verticalLineTo(19f);horizontalLineTo(16f)}
    val SwapHoriz=icon("Internal transfer"){moveTo(3f,8f);horizontalLineTo(20f);lineTo(16f,4f);moveTo(21f,16f);horizontalLineTo(4f);lineTo(8f,20f)}
}
