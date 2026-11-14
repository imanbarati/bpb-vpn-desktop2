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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnLock
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
import com.example.ui.dialogs.QrScannerDialog
import com.example.ui.dialogs.SetupWizardDialog
import com.example.ui.dialogs.SpeedTestDialog
import com.example.ui.dialogs.VpnConsentDialog
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
    ROUTING("Routing", Icons.AutoMirrored.Filled.AltRoute, Icons.AutoMirrored.Outlined.AltRoute, "nav_routing"),
    CONSOLE("Console", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_console"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showSetupWizardDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showSpeedTestDialog by remember { mutableStateOf(false) }
    var showVpnConsentDialog by remember { mutableStateOf(false) }

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
    val hasAcceptedVpnDisclosure by viewModel.hasAcceptedVpnDisclosure.collectAsState()
    val lowestLatencyServerId by viewModel.lowestLatencyServerId.collectAsState()
    val isPeriodicPingRunning by viewModel.isPeriodicPingRunning.collectAsState()
    val speedTestSession by viewModel.speedTestSession.collectAsState()
    val batteryStats by viewModel.batteryStats.collectAsState()

    // Handle navigation from battery alert notification
    val activity = context as? Activity
    androidx.compose.runtime.LaunchedEffect(activity?.intent) {
        val navTarget = activity?.intent?.getStringExtra("NAVIGATE_TO")
        if (navTarget == "SETTINGS") {
            currentTab = NavigationTab.SETTINGS
        }
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

    val proceedWithVpnToggle: (android.content.Context) -> Unit = { ctx ->
        val prepareIntent = VpnService.prepare(ctx)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.toggleVpn(ctx)
        }
    }

    val handleToggleVpn: (android.content.Context) -> Unit = { ctx ->
        if (connectionState.status == com.example.model.VpnStatus.CONNECTED ||
            connectionState.status == com.example.model.VpnStatus.CONNECTING ||
            hasAcceptedVpnDisclosure
        ) {
            proceedWithVpnToggle(ctx)
        } else {
            showVpnConsentDialog = true
        }
    }

    // Trigger 'Connect on Launch' upon app startup if enabled in SharedPreferences
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.checkAndTriggerConnectOnLaunch(context) { prepareIntent ->
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    // Show setup wizard on first launch
    remember(isWizardCompleted) {
        if (!isWizardCompleted) {
            showSetupWizardDialog = true
        }
        true
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
                            onNavigateToRouting = { currentTab = NavigationTab.ROUTING },
                            onOpenSpeedTest = {
                                showSpeedTestDialog = true
                                if (!speedTestSession.isRunning && speedTestSession.results.isEmpty()) {
                                    viewModel.startNetworkSpeedTest()
                                }
                            },
                            onOpenQrScanner = { showQrScannerDialog = true }
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
                            onOpenAddDialog = { showAddServerDialog = true },
                            onOpenQrScanner = { showQrScannerDialog = true },
                            onOpenSpeedTest = {
                                showSpeedTestDialog = true
                                if (!speedTestSession.isRunning && speedTestSession.results.isEmpty()) {
                                    viewModel.startNetworkSpeedTest()
                                }
                            }
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
                            batteryStats = batteryStats,
                            onUpdateGeneral = { viewModel.updateGeneral(it) },
                            onTriggerTestBatteryAlert = { viewModel.triggerTestBatteryAlert(context) },
                            onLaunchSetupWizard = { showSetupWizardDialog = true },
                            onReviewVpnDisclosure = { showVpnConsentDialog = true }
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
            onAddManual = { profile -> viewModel.addServer(profile) },
            onOpenQrScanner = { showQrScannerDialog = true }
        )
    }

    // QR Code Scanner Dialog (CameraX + ML Kit + Zero-permission Photo Picker)
    if (showQrScannerDialog) {
        QrScannerDialog(
            onDismiss = { showQrScannerDialog = false },
            onQrConfigDecoded = { raw ->
                val ok = viewModel.importFromUri(raw)
                if (ok) {
                    Toast.makeText(context, "Imported configuration from QR code", Toast.LENGTH_SHORT).show()
                }
                ok
            }
        )
    }

    // Network Speed Test Benchmark Dialog (Kotlin Coroutines + Retrofit)
    if (showSpeedTestDialog) {
        SpeedTestDialog(
            session = speedTestSession,
            onStartTest = { viewModel.startNetworkSpeedTest() },
            onStopTest = { viewModel.stopNetworkSpeedTest() },
            onSelectFastest = {
                val selected = viewModel.selectFastestSpeedTestServer()
                if (selected != null) {
                    Toast.makeText(context, "Selected fastest node: ${selected.name}", Toast.LENGTH_SHORT).show()
                    currentTab = NavigationTab.HOME
                }
            },
            onSelectServerId = { id ->
                val target = servers.firstOrNull { it.id == id }
                if (target != null) {
                    viewModel.selectServer(target)
                    currentTab = NavigationTab.HOME
                }
            },
            onDismiss = { showSpeedTestDialog = false }
        )
    }

    // Prominent VpnService Disclosure Dialog (Google Play Policy Requirement)
    if (showVpnConsentDialog) {
        VpnConsentDialog(
            onAccept = {
                viewModel.acceptVpnDisclosure()
                showVpnConsentDialog = false
                proceedWithVpnToggle(context)
            },
            onDecline = { showVpnConsentDialog = false }
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
