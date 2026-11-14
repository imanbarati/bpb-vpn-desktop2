package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.ServerSpeedResult
import com.example.service.SpeedTestSessionState
import com.example.service.SpeedTestStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RedDisconnect
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SpeedTestDialog(
    session: SpeedTestSessionState,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onSelectFastest: () -> Unit,
    onSelectServerId: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val fastestResult = session.results.firstOrNull { it.serverId == session.fastestServerId }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172B))
                .border(1.5.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NETWORK SPEED BENCHMARK",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Ping & Bandwidth Ranking",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Speed Test", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress indicator
                val progress = if (session.totalCount > 0) {
                    session.completedCount.toFloat() / session.totalCount.toFloat()
                } else 0f

                if (session.isRunning) {
                    Text(
                        text = "Testing ${session.currentServerName ?: "..."} (${session.completedCount}/${session.totalCount})",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = CyanPrimary,
                        trackColor = CardBackground,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Fastest Server Highlight Card
                if (fastestResult != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(EmeraldConnected.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldConnected.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = EmeraldConnected, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FASTEST RECOMMENDED SERVER",
                                    color = EmeraldConnected,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fastestResult.serverName,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Ping: ${fastestResult.pingMs} ms (±${fastestResult.jitterMs} ms)",
                                    color = EmeraldConnected,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "DL: ${formatMbps(fastestResult.downloadMbps)}",
                                    color = CyanPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "UL: ${formatMbps(fastestResult.uploadMbps)}",
                                    color = ElectricBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Results List
                if (session.results.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBackground)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Tap Start Benchmark to measure real TCP/HTTP ping, jitter, download, and upload throughput across your servers via Retrofit & Coroutines.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .testTag("speed_test_results_list")
                    ) {
                        items(session.results, key = { it.serverId }) { item ->
                            SpeedResultRow(
                                result = item,
                                isFastest = item.serverId == session.fastestServerId,
                                onClick = {
                                    if (item.isReachable) {
                                        onSelectServerId(item.serverId)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = if (session.isRunning) onStopTest else onStartTest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_speed_test_button")
                    ) {
                        Icon(
                            imageVector = if (session.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (session.isRunning) RedDisconnect else CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (session.isRunning) "Stop" else "Run Benchmark",
                            color = if (session.isRunning) RedDisconnect else CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            onSelectFastest()
                            onDismiss()
                        },
                        enabled = fastestResult != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldConnected,
                            contentColor = Color(0xFF002B1B)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_fastest_server_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Fastest", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedResultRow(
    result: ServerSpeedResult,
    isFastest: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isFastest -> EmeraldConnected.copy(alpha = 0.7f)
        result.status == SpeedTestStatus.TESTING_PING || result.status == SpeedTestStatus.TESTING_BANDWIDTH -> CyanPrimary
        result.status == SpeedTestStatus.FAILED -> RedDisconnect.copy(alpha = 0.4f)
        else -> CardBorder
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = result.isReachable) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("speed_result_${result.serverId}")
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.serverName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFastest) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FASTEST",
                        color = EmeraldConnected,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "${result.protocolLabel} • ${result.address}:${result.port}",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            when (result.status) {
                SpeedTestStatus.PENDING -> {
                    Text("Queued", color = TextMuted, fontSize = 10.sp)
                }
                SpeedTestStatus.TESTING_PING -> {
                    Text("Pinging...", color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                SpeedTestStatus.TESTING_BANDWIDTH -> {
                    Text("${result.pingMs} ms • Measuring...", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                SpeedTestStatus.FAILED -> {
                    Text("Unreachable", color = RedDisconnect, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                SpeedTestStatus.COMPLETED -> {
                    Text(
                        text = "${result.pingMs} ms",
                        color = EmeraldConnected,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "↓${formatMbps(result.downloadMbps)} ↑${formatMbps(result.uploadMbps)}",
                        color = CyanPrimary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatMbps(mbps: Double): String {
    return if (mbps <= 0.0) "--" else String.format(Locale.US, "%.1f Mbps", mbps)
}
