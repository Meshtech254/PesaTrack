package ke.pesatrail.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries

@Composable internal fun TrendChart(groups: List<Pair<String,Long>>) {
    val producer=remember{CartesianChartModelProducer()}
    LaunchedEffect(groups) { producer.runTransaction { columnSeries { series(groups.map { java.math.BigDecimal.valueOf(it.second,2).toDouble() }) } } }
    CartesianChartHost(rememberCartesianChart(rememberColumnCartesianLayer(),startAxis=VerticalAxis.rememberStart()),producer,modifier=Modifier.fillMaxWidth().height(170.dp))
}
