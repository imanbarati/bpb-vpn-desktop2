package com.example

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dialogs.AddServerDialog
import com.example.ui.dialogs.SetupWizardDialog
import com.example.ui.screens.AntiCensorshipScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

enum class NavigationTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.VpnLock, Icons.Outlined.VpnLock, "nav_home"),
    SERVERS("Servers", Icons.Filled.Dns, Icons.Outlined.Dns, "nav_servers"),
    ANTI_DPI("Anti-DPI", Icons.Filled.Security, Icons.Outlined.Security, "nav_anti_dpi"),
    ROUTING("Routing", Icons.Filled.AltRoute, Icons.Outlined.AltRoute, "nav_routing"),
    CONSOLE("Console", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_console"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showSetupWizardDialog by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val bandwidthStats by viewModel.bandwidthStats.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val activeServerId by viewModel.activeServerId.collectAsState()
    val antiCensorshipSettings by viewModel.antiCensorshipSettings.collectAsState()
    val routingSettings by viewModel.routingSettings.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val cleanIpResults by viewModel.cleanIpResults.collectAsState()
    val isScanningCleanIps by viewModel.isScanningCleanIps.collectAsState()
    val mtuProbeStep by viewModel.mtuProbeStep.collectAsState()
    val isScanningMtu by viewModel.isScanningMtu.collectAsState()
    val isWizardCompleted by viewModel.isWizardCompleted.collectAsState()
    val lowestLatencyServerId by viewModel.lowestLatencyServerId.collectAsState()
    val isPeriodicPingRunning by viewModel.isPeriodicPingRunning.collectAsState()

    // Show setup wizard on first launch
    remember(isWizardCompleted) {
        if (!isWizardCompleted) {
            showSetupWizardDialog = true
        }
        true
    }

    // Android VpnService permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpn(context)
        } else {
            Toast.makeText(context, "VPN permission is required to route encrypted traffic", Toast.LENGTH_SHORT).show()
        }
    }

    val handleToggleVpn: (android.content.Context) -> Unit = { ctx ->
        val prepareIntent = VpnService.prepare(ctx)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.toggleVpn(ctx)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CardBackground,
                contentColor = CyanPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder)
                    .navigationBarsPadding()
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .statusBarsPadding()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { tab ->
                when (tab) {
                    NavigationTab.HOME -> {
                        HomeScreen(
                            connectionState = connectionState,
                            selectedServer = selectedServer,
                            bandwidthStats = bandwidthStats,
                            antiCensorshipSettings = antiCensorshipSettings,
                            routingSettings = routingSettings,
                            onToggleVpn = handleToggleVpn,
                            onNavigateToServers = { currentTab = NavigationTab.SERVERS },
                            onNavigateToAntiCensorship = { currentTab = NavigationTab.ANTI_DPI },
                            onNavigateToRouting = { currentTab = NavigationTab.ROUTING }
                        )
                    }
                    NavigationTab.SERVERS -> {
                        ServersScreen(
                            servers = servers,
                            selectedServerId = activeServerId,
                            lowestLatencyServerId = lowestLatencyServerId,
                            isPeriodicPingRunning = isPeriodicPingRunning,
                            onTogglePeriodicPing = { viewModel.togglePeriodicPing() },
                            onSelectServer = { server ->
                                viewModel.selectServer(server)
                                currentTab = NavigationTab.HOME
                            },
                            onToggleFavorite = { server -> viewModel.toggleFavorite(server) },
                            onDeleteServer = { server -> viewModel.deleteServer(server) },
                            onPingServer = { server -> viewModel.pingServer(server) },
                            onPingAll = { viewModel.pingAllServers() },
                            onOpenAddDialog = { showAddServerDialog = true }
                        )
                    }
                    NavigationTab.ANTI_DPI -> {
                        AntiCensorshipScreen(
                            settings = antiCensorshipSettings,
                            cleanIpResults = cleanIpResults,
                            isScanningCleanIps = isScanningCleanIps,
                            mtuProbeStep = mtuProbeStep,
                            isScanningMtu = isScanningMtu,
                            onUpdateSettings = { viewModel.updateAntiCensorship(it) },
                            onStartCleanIpScan = { viewModel.startCleanIpScan() },
                            onApplyCleanIp = { ip ->
                                viewModel.applyCleanIpToSelectedServer(ip)
                                Toast.makeText(context, "Clean Anycast IP applied to selected server", Toast.LENGTH_SHORT).show()
                            },
                            onStartMtuProbe = { viewModel.startMtuProbe() },
                            onApplyMtu = { mtu ->
                                viewModel.applyMtu(mtu)
                                Toast.makeText(context, "Applied MTU $mtu bytes to VPN", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    NavigationTab.ROUTING -> {
                        RoutingScreen(
                            routingSettings = routingSettings,
                            installedApps = installedApps,
                            onUpdateRouting = { viewModel.updateRouting(it) },
                            onToggleApp = { pkg -> viewModel.toggleAppSelection(pkg) }
                        )
                    }
                    NavigationTab.CONSOLE -> {
                        LogsScreen(
                            logs = logs,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                    NavigationTab.SETTINGS -> {
                        SettingsScreen(
                            generalSettings = generalSettings,
                            onUpdateGeneral = { viewModel.updateGeneral(it) },
                            onLaunchSetupWizard = { showSetupWizardDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Add Server Dialog
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onImportUri = { uri -> viewModel.importFromUri(uri) },
            onAddManual = { profile -> viewModel.addServer(profile) }
        )
    }

    // Setup Wizard Dialog
    if (showSetupWizardDialog) {
        SetupWizardDialog(
            onDismiss = {
                showSetupWizardDialog = false
                viewModel.setWizardCompleted()
            }
        )
    }
}
