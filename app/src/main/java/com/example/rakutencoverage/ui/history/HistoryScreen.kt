package com.example.rakutencoverage.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.ui.map.MapViewModel

@Composable
fun HistoryScreen(vm: MapViewModel = viewModel()) {
    val measurements by vm.measurements.collectAsState()

    if (measurements.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("まだ計測データがありません")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SummaryCard(measurements)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(measurements) { m ->
                MeasurementRow(m)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SummaryCard(measurements: List<Measurement>) {
    val counts = measurements.groupBy { it.signalLevel }.mapValues { it.value.size }
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("計測サマリー（合計 ${measurements.size} 件）",
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalLevel.entries.forEach { level ->
                    val count = counts[level] ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(level.toComposeColor(), CircleShape)
                        )
                        Text("${count}", style = MaterialTheme.typography.labelSmall)
                        Text(level.shortLabel(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(m: Measurement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(m.signalLevel.toComposeColor(), CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("${m.networkType}  Band: ${m.band ?: "?"}", style = MaterialTheme.typography.bodySmall)
            Text("RTT ${m.rttMs}ms  |  ${m.timestamp.take(19).replace("T", " ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("%.4f, %.4f".format(m.latitude, m.longitude),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun SignalLevel.toComposeColor() = when (this) {
    SignalLevel.MILLIMETER_WAVE -> Color(0xFFFF6F00)
    SignalLevel.PLATINUM_5G   -> Color(0xFFFFD700)
    SignalLevel.FIVE_G        -> Color(0xFF1E88E5)
    SignalLevel.PLATINUM      -> Color(0xFFAB47BC)
    SignalLevel.LTE           -> Color(0xFF43A047)
    SignalLevel.WEAK          -> Color(0xFF757575)
    SignalLevel.NO_SIGNAL     -> Color(0xFF212121)
    SignalLevel.AIRPLANE_MODE -> Color(0xFF1565C0)
    SignalLevel.NO_SIM        -> Color(0xFFB71C1C)
}

private fun SignalLevel.shortLabel() = when (this) {
    SignalLevel.MILLIMETER_WAVE -> "mmW"
    SignalLevel.PLATINUM_5G   -> "Pt5G"
    SignalLevel.FIVE_G        -> "5G"
    SignalLevel.PLATINUM      -> "Pt"
    SignalLevel.LTE           -> "LTE"
    SignalLevel.WEAK          -> "弱"
    SignalLevel.NO_SIGNAL     -> "圏外"
    SignalLevel.AIRPLANE_MODE -> "機内"
    SignalLevel.NO_SIM        -> "SIM無"
}
