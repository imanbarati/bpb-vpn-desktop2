package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.example.model.GeneralSettings
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

@Composable
fun SettingsScreen(
    generalSettings: GeneralSettings,
    onUpdateGeneral: (GeneralSettings) -> Unit,
    onLaunchSetupWizard: () -> Unit,
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
            text = "PREFERENCES & NETWORK",
            color = CyanPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "DNS, Inbounds & Core Options",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Setup Wizard Card Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { onLaunchSetupWizard() }
                .padding(14.dp)
                .testTag("launch_setup_wizard_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Setup Guide",
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Setup & Worker Guide",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Learn how to deploy BPB Cloudflare Workers and import VLESS links",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Open",
                    color = CyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. DNS Settings Section
        SettingsGroupHeader("ENCRYPTED DNS RESOLVER", Icons.Default.Dns)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Enable DNS over HTTPS (DoH)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = generalSettings.dns.enableDoh,
                        onCheckedChange = { en ->
                            onUpdateGeneral(generalSettings.copy(dns = generalSettings.dns.copy(enableDoh = en)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Upstream Provider", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val providers = listOf(
                    Triple("Cloudflare (1.1.1.1)", "1.1.1.1", "https://1.1.1.1/dns-query"),
                    Triple("Google (8.8.8.8)", "8.8.8.8", "https://dns.google/dns-query"),
                    Triple("AdGuard DoH", "94.140.14.14", "https://dns.adguard-dns.com/dns-query")
                )

                providers.forEach { (name, ip, doh) ->
                    val isSel = generalSettings.dns.provider == name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CyanPrimary.copy(alpha = 0.15f) else Color(0xFF0C1425))
                            .border(1.dp, if (isSel) CyanPrimary else CardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                onUpdateGeneral(
                                    generalSettings.copy(
                                        dns = generalSettings.dns.copy(
                                            provider = name,
                                            primaryDns = ip,
                                            dohUrl = doh
                                        )
                                    )
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(text = name, color = if (isSel) CyanPrimary else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(text = ip, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Inbound Proxy Ports Section
        SettingsGroupHeader("LOCAL INBOUND PORTS", Icons.Default.Lan)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "SOCKS5 Proxy Port", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "127.0.0.1:${generalSettings.socksPort}", color = CyanPrimary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "HTTP Proxy Port", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "127.0.0.1:${generalSettings.httpPort}", color = ElectricBlue, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Allow LAN Connections", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Binds to 0.0.0.0 so other devices on Wi-Fi can proxy through your phone", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = generalSettings.allowLanSharing,
                        onCheckedChange = { en ->
                            onUpdateGeneral(generalSettings.copy(allowLanSharing = en))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Security & Automation
        SettingsGroupHeader("SECURITY & AUTOMATION", Icons.Default.Security)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Kill Switch (Block Non-VPN)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Disables non-encrypted traffic if tunnel unexpectedly drops", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = generalSettings.killSwitchEnabled,
                        onCheckedChange = { en ->
                            onUpdateGeneral(generalSettings.copy(killSwitchEnabled = en))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RedDisconnect)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Auto-Connect on Device Boot", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Starts the VPN service automatically after reboot", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = generalSettings.autoConnectOnBoot,
                        onCheckedChange = { en ->
                            onUpdateGeneral(generalSettings.copy(autoConnectOnBoot = en))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0F1D))
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(text = "BPB VPN Core v2.4 (Android Edition)", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "High-performance circumvention tool powered by Xray-core, Cloudflare Workers, and TLS packet fragmentation.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGroupHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
