package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AntiCensorshipSettings
import com.example.model.FragmentConfig
import com.example.model.MtuConfig
import com.example.model.NoiseConfig
import com.example.model.TorBridgeConfig
import com.example.model.WarpConfig
import com.example.service.CleanIpResult
import com.example.service.IpQuality
import com.example.service.MtuProbeStep
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RedDisconnect
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AntiCensorshipScreen(
    settings: AntiCensorshipSettings,
    cleanIpResults: List<CleanIpResult>,
    isScanningCleanIps: Boolean,
    mtuProbeStep: MtuProbeStep?,
    isScanningMtu: Boolean,
    onUpdateSettings: (AntiCensorshipSettings) -> Unit,
    onStartCleanIpScan: () -> Unit,
    onApplyCleanIp: (String) -> Unit,
    onStartMtuProbe: () -> Unit,
    onApplyMtu: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Title Header
        Text(
            text = "ANTI-CENSORSHIP TOOLKIT",
            color = CyanPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Deep Packet Evader & Tuning",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. TLS Fragmentation Card
        ToolCard(
            title = "TLS ClientHello Fragmentation",
            icon = Icons.Default.Grain,
            iconTint = CyanPrimary,
            enabled = settings.fragment.enabled,
            onToggle = { enabled ->
                onUpdateSettings(settings.copy(fragment = settings.fragment.copy(enabled = enabled)))
            }
        ) {
            Text(
                text = "Splits the TLS handshake into tiny TCP packets so stateful firewalls cannot inspect the SNI (Server Name Indication).",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Length Range", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PresetSelector(
                        selected = settings.fragment.length,
                        options = listOf("10-50", "20-80", "1-5"),
                        onSelect = { len ->
                            onUpdateSettings(settings.copy(fragment = settings.fragment.copy(length = len)))
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Interval (ms)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PresetSelector(
                        selected = settings.fragment.interval,
                        options = listOf("10-30", "5-15", "15-40"),
                        onSelect = { intv ->
                            onUpdateSettings(settings.copy(fragment = settings.fragment.copy(interval = intv)))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Noise Obfuscation Card
        ToolCard(
            title = "Noise Packet Injection",
            icon = Icons.Default.Bolt,
            iconTint = AmberWarning,
            enabled = settings.noise.enabled,
            onToggle = { enabled ->
                onUpdateSettings(settings.copy(noise = settings.noise.copy(enabled = enabled)))
            }
        ) {
            Text(
                text = "Pre-pends random padding and pseudo-QUIC headers before the stream to disrupt heuristic pattern matching and DPI fingerprinters.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Payload Style:", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(10.dp))
                PresetSelector(
                    selected = settings.noise.payloadType,
                    options = listOf("quic", "rand", "str"),
                    onSelect = { p ->
                        onUpdateSettings(settings.copy(noise = settings.noise.copy(payloadType = p)))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. MTU Path Discovery (MTU Finder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricBlue.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "MTU Finder",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MTU Path Finder",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current MTU: ${settings.mtu.mtu} Bytes",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onStartMtuProbe,
                        enabled = !isScanningMtu,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("run_mtu_probe_button")
                    ) {
                        if (isScanningMtu) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Test MTU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Probes path MTU with Don't-Fragment (DF) packets to eliminate packet dropping caused by restrictive ISP MSS clamps.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                if (isScanningMtu && mtuProbeStep != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { mtuProbeStep.progressPercent / 100f },
                        color = ElectricBlue,
                        trackColor = CardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mtuProbeStep.message,
                        color = CyanPrimary,
                        fontSize = 11.sp
                    )
                }

                if (!isScanningMtu && settings.mtu.lastDiscoveredMtu > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0C1425))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Recommended MTU: ${settings.mtu.lastDiscoveredMtu} B",
                            color = EmeraldConnected,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (settings.mtu.mtu != settings.mtu.lastDiscoveredMtu) {
                            Text(
                                text = "Apply",
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onApplyMtu(settings.mtu.lastDiscoveredMtu) }
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Applied", tint = EmeraldConnected, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Cloudflare Anycast Clean IP Scanner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldConnected.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Clean IP",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloudflare Clean IP Scanner",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Find unthrottled Anycast edge IPs",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = onStartCleanIpScan,
                        enabled = !isScanningCleanIps,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldConnected,
                            contentColor = Color(0xFF003915)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("scan_clean_ips_button")
                    ) {
                        if (isScanningCleanIps) {
                            CircularProgressIndicator(
                                color = Color(0xFF003915),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("Scan Clean IPs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (cleanIpResults.isNotEmpty()) {
                    Text(
                        text = "Top Responsive IPs for your ISP:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    cleanIpResults.take(4).forEach { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C1425))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            val qualityColor = when (res.quality) {
                                IpQuality.EXCELLENT -> EmeraldConnected
                                IpQuality.GOOD -> CyanPrimary
                                IpQuality.FAIR -> AmberWarning
                                IpQuality.UNREACHABLE -> RedDisconnect
                            }

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(qualityColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = res.ip, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = res.ispLocation, color = TextMuted, fontSize = 10.sp)
                            }
                            Text(
                                text = "${res.latencyMs} ms",
                                color = qualityColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Use IP",
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onApplyCleanIp(res.ip) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Cloudflare WARP+ and Tor Bridges
        ToolCard(
            title = "WARP+ WireGuard Chaining",
            icon = Icons.Default.Shield,
            iconTint = Color(0xFFFF9100),
            enabled = settings.warp.enabled,
            onToggle = { enabled ->
                onUpdateSettings(settings.copy(warp = settings.warp.copy(enabled = enabled)))
            }
        ) {
            Text(
                text = "Routes encrypted traffic over Cloudflare WARP Anycast backhaul before reaching your target exit.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        ToolCard(
            title = "Tor Bridge Fallback",
            icon = Icons.Default.Security,
            iconTint = Color(0xFFAB47BC),
            enabled = settings.tor.enabled,
            onToggle = { enabled ->
                onUpdateSettings(settings.copy(tor = settings.tor.copy(enabled = enabled)))
            }
        ) {
            Text(
                text = "Integrates Snowflake & WebTunnel bridges to maintain connectivity when local ISPs enforce full IP blocking.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ToolCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, if (enabled) iconTint.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f))
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = iconTint,
                        uncheckedTrackColor = Color(0xFF0C1425)
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PresetSelector(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            val isSel = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) CyanPrimary.copy(alpha = 0.2f) else Color(0xFF0C1425))
                    .border(1.dp, if (isSel) CyanPrimary else CardBorder, RoundedCornerShape(8.dp))
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = opt,
                    color = if (isSel) CyanPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
