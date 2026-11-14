package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AntiCensorshipSettings
import com.example.model.BandwidthStats
import com.example.model.ConnectionState
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import com.example.model.VpnStatus
import com.example.ui.components.ConnectionButton
import com.example.ui.components.MetricCard
import com.example.ui.components.SpeedChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RedDisconnect
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HomeScreen(
    connectionState: ConnectionState,
    selectedServer: ServerProfile?,
    bandwidthStats: BandwidthStats,
    antiCensorshipSettings: AntiCensorshipSettings,
    routingSettings: RoutingSettings,
    onToggleVpn: (Context) -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToAntiCensorship: () -> Unit,
    onNavigateToRouting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Security Status Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BPB FREEDOM VPN",
                    color = CyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (connectionState.status) {
                        VpnStatus.CONNECTED -> "Encrypted & DPI Bypassed"
                        VpnStatus.CONNECTING -> "Establishing Tunnel..."
                        VpnStatus.DISCONNECTING -> "Disconnecting..."
                        VpnStatus.ERROR -> "Connection Failed"
                        VpnStatus.DISCONNECTED -> "Traffic Unprotected"
                    },
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status Capsule
            val statusColor = when (connectionState.status) {
                VpnStatus.CONNECTED -> EmeraldConnected
                VpnStatus.CONNECTING -> AmberWarning
                VpnStatus.DISCONNECTING -> RedDisconnect
                VpnStatus.ERROR -> RedDisconnect
                VpnStatus.DISCONNECTED -> TextMuted
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = connectionState.status.name,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center Animated Connect Button
        ConnectionButton(
            status = connectionState.status,
            onClick = { onToggleVpn(context) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Selected Server Selector Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .clickable { onNavigateToServers() }
                .padding(14.dp)
                .testTag("select_server_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val flagEmoji = when (selectedServer?.countryCode?.uppercase()) {
                    "US" -> "🇺🇸"
                    "DE" -> "🇩🇪"
                    "NL" -> "🇳🇱"
                    "SG" -> "🇸🇬"
                    "CA" -> "🇨🇦"
                    "GB", "UK" -> "🇬🇧"
                    "JP" -> "🇯🇵"
                    "FR" -> "🇫🇷"
                    else -> "🌐"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C1425))
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Text(text = flagEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedServer?.name ?: "No Server Selected",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val backendHint = when (selectedServer?.protocol) {
                        com.example.model.ProtocolType.FIREFOX_VPN -> "Mozilla/Fastly H2+H3 Free Pool"
                        com.example.model.ProtocolType.REVERSE_TUNNEL -> "Adaptive Reverse Tunneler"
                        else -> selectedServer?.address ?: "Tap to choose"
                    }
                    Text(
                        text = "${selectedServer?.protocol?.name?.replace("_", " ") ?: "VLESS"} • $backendHint",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Change Server",
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Real-Time Speed & Telemetry Grid
        val downSpeedStr = formatSpeed(bandwidthStats.downloadSpeedBps)
        val upSpeedStr = formatSpeed(bandwidthStats.uploadSpeedBps)
        val durationStr = formatDuration(bandwidthStats.sessionDurationSeconds)
        val pingStr = if (bandwidthStats.pingMs > 0) "${bandwidthStats.pingMs}" else (selectedServer?.pingMs?.takeIf { it > 0 }?.toString() ?: "--")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "DOWNLOAD",
                value = downSpeedStr.first,
                unit = downSpeedStr.second,
                icon = Icons.Default.Download,
                iconTint = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "UPLOAD",
                value = upSpeedStr.first,
                unit = upSpeedStr.second,
                icon = Icons.Default.Upload,
                iconTint = ElectricBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "PING / RTT",
                value = pingStr,
                unit = "ms",
                icon = Icons.Default.NetworkPing,
                iconTint = EmeraldConnected,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "UPTIME",
                value = durationStr,
                unit = "h:m:s",
                icon = Icons.Default.Timer,
                iconTint = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Real-Time Speed Chart Waveform
        SpeedChart(
            downloadSpeedBps = bandwidthStats.downloadSpeedBps,
            uploadSpeedBps = bandwidthStats.uploadSpeedBps
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Anti-Censorship Features Quick Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ANTI-CENSORSHIP SHIELD",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Configure",
                color = CyanPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onNavigateToAntiCensorship() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeatureChip(
                label = "TLS Fragments",
                active = antiCensorshipSettings.fragment.enabled,
                icon = Icons.Default.Grain,
                onClick = onNavigateToAntiCensorship,
                modifier = Modifier.weight(1f)
            )
            FeatureChip(
                label = "Noise Injection",
                active = antiCensorshipSettings.noise.enabled,
                icon = Icons.Default.Bolt,
                onClick = onNavigateToAntiCensorship,
                modifier = Modifier.weight(1f)
            )
            FeatureChip(
                label = "WARP+ Chain",
                active = antiCensorshipSettings.warp.enabled,
                icon = Icons.Default.Shield,
                onClick = onNavigateToAntiCensorship,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FeatureChip(
    label: String,
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = if (active) CyanPrimary else TextMuted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, if (active) activeColor.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (active) "ACTIVE" else "OFF",
                color = if (active) EmeraldConnected else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): Pair<String, String> {
    if (bytesPerSec <= 0) return Pair("0.0", "KB/s")
    val kb = bytesPerSec / 1024.0
    return if (kb > 1024.0) {
        Pair(String.format(Locale.US, "%.2f", kb / 1024.0), "MB/s")
    } else {
        Pair(String.format(Locale.US, "%.1f", kb), "KB/s")
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
