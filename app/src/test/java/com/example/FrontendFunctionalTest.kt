package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.DefaultServers
import com.example.model.AntiCensorshipSettings
import com.example.model.BandwidthStats
import com.example.model.ConnectionState
import com.example.model.GeneralSettings
import com.example.model.ProtocolType
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import com.example.model.VpnStatus
import com.example.service.BatteryDrainMonitor
import com.example.service.BatteryDrainStats
import com.example.service.UriParser
import com.example.service.VpnController
import com.example.ui.dialogs.AddServerDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FrontendFunctionalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        VpnController.resetForTesting()
        VpnController.connectDelayMs = 20L
        VpnController.disconnectDelayMs = 20L
    }

    @After
    fun tearDown() {
        VpnController.resetForTesting()
    }

    @Test
    fun `main app navigates across all tabs and dismisses setup wizard`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // First-launch setup wizard should appear; dismiss it
        composeTestRule.onNodeWithTag("dismiss_wizard_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify Home tab active
        composeTestRule.onNodeWithText("BPB FREEDOM VPN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vpn_power_button").assertIsDisplayed()

        // Navigate to Servers tab
        composeTestRule.onNodeWithTag("nav_servers").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SERVERS & NODES").assertIsDisplayed()

        // Navigate to Anti-DPI tab
        composeTestRule.onNodeWithTag("nav_anti_dpi").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ANTI-CENSORSHIP TOOLKIT").assertIsDisplayed()

        // Navigate to Routing tab
        composeTestRule.onNodeWithTag("nav_routing").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TRAFFIC & APP ROUTING").assertIsDisplayed()

        // Navigate to Console tab
        composeTestRule.onNodeWithTag("nav_console").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CONSOLE & TELEMETRY").assertIsDisplayed()

        // Navigate to Settings tab
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PREFERENCES & NETWORK").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_on_launch_switch").performScrollTo().assertIsDisplayed()

        // Navigate back to Home
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vpn_power_button").assertIsDisplayed()
    }

    @Test
    fun `home screen connect button toggles vpn connection state and displays backend hint`() {
        val fxServer = DefaultServers.getInitialServers()
            .first { it.protocol == ProtocolType.FIREFOX_VPN }

        composeTestRule.setContent {
            MyApplicationTheme {
                var status by remember { mutableStateOf(VpnStatus.DISCONNECTED) }
                HomeScreen(
                    connectionState = ConnectionState(status = status, activeProfileId = fxServer.id, activeProfile = fxServer),
                    selectedServer = fxServer,
                    bandwidthStats = BandwidthStats(
                        downloadSpeedBps = if (status == VpnStatus.CONNECTED) 2_500_000L else 0L,
                        uploadSpeedBps = if (status == VpnStatus.CONNECTED) 350_000L else 0L,
                        pingMs = fxServer.pingMs
                    ),
                    antiCensorshipSettings = AntiCensorshipSettings(),
                    routingSettings = RoutingSettings(),
                    onToggleVpn = {
                        status = if (status == VpnStatus.DISCONNECTED) VpnStatus.CONNECTED else VpnStatus.DISCONNECTED
                    },
                    onNavigateToServers = {},
                    onNavigateToAntiCensorship = {},
                    onNavigateToRouting = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Check Firefox VPN backend hint renders on HomeScreen
        composeTestRule.onNodeWithText("FIREFOX VPN • Mozilla/Fastly H2+H3 Free Pool").assertIsDisplayed()
        composeTestRule.onNodeWithText("Traffic Unprotected").assertIsDisplayed()

        // Tap connect button -> transitions to CONNECTED
        composeTestRule.onNodeWithTag("vpn_power_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Encrypted & DPI Bypassed").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("CONNECTED").fetchSemanticsNodes().isNotEmpty())

        // Tap again -> transitions back to DISCONNECTED
        composeTestRule.onNodeWithTag("vpn_power_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Traffic Unprotected").assertIsDisplayed()
    }

    @Test
    fun `servers screen imports fxvpn uri and creates manual reverse tunnel server`() {
        val importedServers = mutableListOf<ServerProfile>()

        composeTestRule.setContent {
            MyApplicationTheme {
                var servers by remember { mutableStateOf(DefaultServers.getInitialServers()) }
                var selectedId by remember { mutableStateOf<String?>(servers.first().id) }
                var showAdd by remember { mutableStateOf(false) }

                ServersScreen(
                    servers = servers,
                    selectedServerId = selectedId,
                    onSelectServer = { selectedId = it.id },
                    onToggleFavorite = {},
                    onDeleteServer = {},
                    onPingServer = {},
                    onPingAll = {},
                    onOpenAddDialog = { showAdd = true }
                )

                if (showAdd) {
                    AddServerDialog(
                        onDismiss = { showAdd = false },
                        onImportUri = { raw ->
                            val parsed = UriParser.parse(raw) ?: return@AddServerDialog false
                            importedServers.add(parsed)
                            servers = listOf(parsed) + servers
                            selectedId = parsed.id
                            true
                        },
                        onAddManual = { manual ->
                            importedServers.add(manual)
                            servers = listOf(manual) + servers
                            selectedId = manual.id
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Import fxvpn:// URI via AddServerDialog
        composeTestRule.onNodeWithTag("add_server_fab").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("uri_input_field")
            .performScrollTo()
            .performTextInput("fxvpn://tok-free@fastly.fx.net:443?type=h2#My-Firefox-Node")
        composeTestRule.onNodeWithTag("confirm_import_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(importedServers.any { it.protocol == ProtocolType.FIREFOX_VPN && it.name == "My-Firefox-Node" })
        composeTestRule.onNodeWithTag("servers_list").performScrollToNode(hasText("My-Firefox-Node"))
        composeTestRule.onNodeWithText("My-Firefox-Node").assertIsDisplayed()

        // 2. Add REVERSE_TUNNEL manually via protocol chip selector
        composeTestRule.onNodeWithTag("add_server_fab").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_manual_config").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("manual_protocol_row")
            .performScrollTo()
            .performScrollToNode(hasTestTag("manual_protocol_REVERSE_TUNNEL"))
        composeTestRule.onNodeWithTag("manual_protocol_REVERSE_TUNNEL").performClick()
        composeTestRule.onNodeWithTag("manual_name_input").performScrollTo().performTextInput("Robot-Manual-Node")
        composeTestRule.onNodeWithTag("manual_host_input").performScrollTo().performTextInput("robot-relay.example.com")
        composeTestRule.onNodeWithTag("save_node_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            importedServers.any {
                it.protocol == ProtocolType.REVERSE_TUNNEL &&
                    it.name == "Robot-Manual-Node" &&
                    it.address == "robot-relay.example.com"
            }
        )
        composeTestRule.onNodeWithTag("servers_list").performScrollToNode(hasText("Robot-Manual-Node"))
        composeTestRule.onNodeWithText("Robot-Manual-Node").assertIsDisplayed()
    }

    @Test
    fun `settings screen toggles connect on launch and triggers battery alert ui`() {
        val repo = com.example.data.AppPreferencesRepository(context)
        repo.updateGeneralSettings(GeneralSettings())

        composeTestRule.setContent {
            MyApplicationTheme {
                var settings by remember { mutableStateOf(repo.generalSettings.value) }
                var batteryStats by remember { mutableStateOf(BatteryDrainStats(currentBatteryLevel = 78)) }

                SettingsScreen(
                    generalSettings = settings,
                    batteryStats = batteryStats,
                    onUpdateGeneral = { updated ->
                        repo.updateGeneralSettings(updated)
                        settings = updated
                    },
                    onTriggerTestBatteryAlert = {
                        BatteryDrainMonitor.triggerTestAlert(context)
                        batteryStats = BatteryDrainMonitor.stats.value
                    },
                    onLaunchSetupWizard = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Toggle Connect on App Launch
        composeTestRule.onNodeWithTag("connect_on_launch_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, repo.generalSettings.value.connectOnLaunch)

        // Trigger Battery Alert test button
        composeTestRule.onNodeWithTag("test_battery_alert_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithText("⚠️ Test alert triggered: 4% simulated drop while idling in background").fetchSemanticsNodes().isNotEmpty())
    }
}
