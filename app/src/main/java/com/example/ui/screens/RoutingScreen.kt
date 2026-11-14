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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppItem
import com.example.model.RoutingMode
import com.example.model.RoutingSettings
import com.example.model.SplitTunnelMode
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RoutingScreen(
    routingSettings: RoutingSettings,
    installedApps: List<AppItem>,
    onUpdateRouting: (RoutingSettings) -> Unit,
    onToggleApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var appSearchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Title Header
        Text(
            text = "TRAFFIC & APP ROUTING",
            color = CyanPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Split Tunneling & Domain Rules",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs: 0: Global Routing, 1: Per-App Split Tunneling
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyanPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanPrimary
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Routing Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Per-App Proxy (${routingSettings.selectedPackages.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Routing Modes List
            Column {
                RoutingModeCard(
                    title = "Bypass LAN & Domestic",
                    subtitle = "Recommended for censored regions (Iran, China, Russia). Domestic sites load direct; blocked services routed through proxy.",
                    isSelected = routingSettings.mode == RoutingMode.BYPASS_LAN_AND_DOMESTIC,
                    onSelect = { onUpdateRouting(routingSettings.copy(mode = RoutingMode.BYPASS_LAN_AND_DOMESTIC)) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                RoutingModeCard(
                    title = "Global Proxy (All Traffic)",
                    subtitle = "Routes 100% of device connections through the encrypted VPN tunnel.",
                    isSelected = routingSettings.mode == RoutingMode.GLOBAL_PROXY,
                    onSelect = { onUpdateRouting(routingSettings.copy(mode = RoutingMode.GLOBAL_PROXY)) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                RoutingModeCard(
                    title = "Custom Domain & IP Rules",
                    subtitle = "Applies custom blocklist, direct list, and proxy rule matching.",
                    isSelected = routingSettings.mode == RoutingMode.CUSTOM_RULES,
                    onSelect = { onUpdateRouting(routingSettings.copy(mode = RoutingMode.CUSTOM_RULES)) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Built-in rule summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBackground)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "ACTIVE RULES OVERVIEW",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Localhost, 192.168.0.0/16, 10.0.0.0/8: DIRECT\n• Domestic GeoIP & Geosite: DIRECT (when bypass active)\n• Known Adware & Tracking: BLACKHOLE (BLOCKED)\n• Everything else: ENCRYPTED VPN TUNNEL",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            // Per-App Split Tunneling Screen
            Column(modifier = Modifier.fillMaxSize()) {
                // Toggle Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBackground)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Per-App Split Tunneling",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (routingSettings.splitTunnelEnabled) "Active: selectively routing apps" else "Disabled: all apps use global rule",
                            color = if (routingSettings.splitTunnelEnabled) EmeraldConnected else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = routingSettings.splitTunnelEnabled,
                        onCheckedChange = { en ->
                            onUpdateRouting(routingSettings.copy(splitTunnelEnabled = en))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyanPrimary
                        ),
                        modifier = Modifier.testTag("split_tunnel_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search box
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed applications...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { appSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Apps List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = routingSettings.selectedPackages.contains(app.packageName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardBackground)
                                .border(1.dp, if (isChecked) CyanPrimary.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(10.dp))
                                .clickable { onToggleApp(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0C1425))
                            ) {
                                Text(
                                    text = app.appName.firstOrNull()?.uppercase() ?: "A",
                                    color = CyanPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = app.packageName,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CyanPrimary,
                                    checkmarkColor = Color(0xFF00363D),
                                    uncheckedColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, if (isSelected) CyanPrimary else CardBorder, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CyanPrimary,
                    unselectedColor = TextMuted
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
