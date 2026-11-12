package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AntiCensorshipSettings
import com.example.model.DnsSettings
import com.example.model.FragmentConfig
import com.example.model.GeneralSettings
import com.example.model.MtuConfig
import com.example.model.NoiseConfig
import com.example.model.RoutingMode
import com.example.model.RoutingSettings
import com.example.model.SplitTunnelMode
import com.example.model.TorBridgeConfig
import com.example.model.WarpConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bpb_vpn_prefs", Context.MODE_PRIVATE)

    private val _activeServerId = MutableStateFlow<String?>(
        prefs.getString(KEY_ACTIVE_SERVER_ID, "bpb-cf-worker-us")
    )
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _antiCensorshipSettings = MutableStateFlow(loadAntiCensorshipSettings())
    val antiCensorshipSettings: StateFlow<AntiCensorshipSettings> = _antiCensorshipSettings.asStateFlow()

    private val _routingSettings = MutableStateFlow(loadRoutingSettings())
    val routingSettings: StateFlow<RoutingSettings> = _routingSettings.asStateFlow()

    private val _generalSettings = MutableStateFlow(loadGeneralSettings())
    val generalSettings: StateFlow<GeneralSettings> = _generalSettings.asStateFlow()

    private val _isWizardCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_WIZARD_COMPLETED, false)
    )
    val isWizardCompleted: StateFlow<Boolean> = _isWizardCompleted.asStateFlow()

    fun setActiveServerId(id: String?) {
        _activeServerId.value = id
        prefs.edit().putString(KEY_ACTIVE_SERVER_ID, id).apply()
    }

    fun updateAntiCensorshipSettings(settings: AntiCensorshipSettings) {
        _antiCensorshipSettings.value = settings
        prefs.edit()
            .putBoolean(KEY_FRAG_ENABLED, settings.fragment.enabled)
            .putString(KEY_FRAG_LENGTH, settings.fragment.length)
            .putString(KEY_FRAG_INTERVAL, settings.fragment.interval)
            .putString(KEY_FRAG_PACKETS, settings.fragment.packets)
            .putBoolean(KEY_NOISE_ENABLED, settings.noise.enabled)
            .putInt(KEY_NOISE_COUNT, settings.noise.packetCount)
            .putString(KEY_NOISE_PAYLOAD, settings.noise.payloadType)
            .putBoolean(KEY_WARP_ENABLED, settings.warp.enabled)
            .putString(KEY_WARP_LICENSE, settings.warp.warpPlusLicense)
            .putBoolean(KEY_TOR_ENABLED, settings.tor.enabled)
            .putString(KEY_TOR_TYPE, settings.tor.bridgeType)
            .putInt(KEY_MTU_VALUE, settings.mtu.mtu)
            .putBoolean(KEY_MTU_AUTO, settings.mtu.autoDetect)
            .apply()
    }

    fun updateRoutingSettings(settings: RoutingSettings) {
        _routingSettings.value = settings
        prefs.edit()
            .putString(KEY_ROUTING_MODE, settings.mode.name)
            .putBoolean(KEY_SPLIT_TUNNEL_ENABLED, settings.splitTunnelEnabled)
            .putString(KEY_SPLIT_TUNNEL_MODE, settings.splitTunnelMode.name)
            .putStringSet(KEY_SELECTED_PACKAGES, settings.selectedPackages)
            .apply()
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        _generalSettings.value = settings
        prefs.edit()
            .putString(KEY_DNS_PROVIDER, settings.dns.provider)
            .putString(KEY_DNS_PRIMARY, settings.dns.primaryDns)
            .putString(KEY_DNS_DOH_URL, settings.dns.dohUrl)
            .putBoolean(KEY_DNS_ENABLE_DOH, settings.dns.enableDoh)
            .putInt(KEY_SOCKS_PORT, settings.socksPort)
            .putInt(KEY_HTTP_PORT, settings.httpPort)
            .putBoolean(KEY_ALLOW_LAN, settings.allowLanSharing)
            .putBoolean(KEY_KILL_SWITCH, settings.killSwitchEnabled)
            .putBoolean(KEY_AUTO_CONNECT, settings.autoConnectOnBoot)
            .putBoolean(KEY_CONNECT_ON_LAUNCH, settings.connectOnLaunch)
            .putBoolean(KEY_BATTERY_MONITOR_ENABLED, settings.batteryMonitorEnabled)
            .putInt(KEY_BATTERY_DRAIN_THRESHOLD, settings.batteryDrainThresholdPercent)
            .apply()
    }

    fun setConnectOnLaunch(enabled: Boolean) {
        val updated = _generalSettings.value.copy(connectOnLaunch = enabled)
        updateGeneralSettings(updated)
    }

    fun setBatteryMonitorEnabled(enabled: Boolean) {
        val updated = _generalSettings.value.copy(batteryMonitorEnabled = enabled)
        updateGeneralSettings(updated)
    }

    fun setWizardCompleted(completed: Boolean) {
        _isWizardCompleted.value = completed
        prefs.edit().putBoolean(KEY_WIZARD_COMPLETED, completed).apply()
    }

    private fun loadAntiCensorshipSettings(): AntiCensorshipSettings {
        return AntiCensorshipSettings(
            fragment = FragmentConfig(
                enabled = prefs.getBoolean(KEY_FRAG_ENABLED, true),
                length = prefs.getString(KEY_FRAG_LENGTH, "10-50") ?: "10-50",
                interval = prefs.getString(KEY_FRAG_INTERVAL, "10-30") ?: "10-30",
                packets = prefs.getString(KEY_FRAG_PACKETS, "tlshello") ?: "tlshello"
            ),
            noise = NoiseConfig(
                enabled = prefs.getBoolean(KEY_NOISE_ENABLED, true),
                packetCount = prefs.getInt(KEY_NOISE_COUNT, 12),
                payloadType = prefs.getString(KEY_NOISE_PAYLOAD, "quic") ?: "quic"
            ),
            warp = WarpConfig(
                enabled = prefs.getBoolean(KEY_WARP_ENABLED, false),
                warpPlusLicense = prefs.getString(KEY_WARP_LICENSE, "") ?: ""
            ),
            tor = TorBridgeConfig(
                enabled = prefs.getBoolean(KEY_TOR_ENABLED, false),
                bridgeType = prefs.getString(KEY_TOR_TYPE, "Snowflake") ?: "Snowflake"
            ),
            mtu = MtuConfig(
                mtu = prefs.getInt(KEY_MTU_VALUE, 1420),
                autoDetect = prefs.getBoolean(KEY_MTU_AUTO, true)
            )
        )
    }

    private fun loadRoutingSettings(): RoutingSettings {
        val modeStr = prefs.getString(KEY_ROUTING_MODE, RoutingMode.BYPASS_LAN_AND_DOMESTIC.name)
        val mode = try { RoutingMode.valueOf(modeStr ?: "") } catch (e: Exception) { RoutingMode.BYPASS_LAN_AND_DOMESTIC }
        val splitModeStr = prefs.getString(KEY_SPLIT_TUNNEL_MODE, SplitTunnelMode.BYPASS_SELECTED_ONLY.name)
        val splitMode = try { SplitTunnelMode.valueOf(splitModeStr ?: "") } catch (e: Exception) { SplitTunnelMode.BYPASS_SELECTED_ONLY }

        return RoutingSettings(
            mode = mode,
            splitTunnelEnabled = prefs.getBoolean(KEY_SPLIT_TUNNEL_ENABLED, false),
            splitTunnelMode = splitMode,
            selectedPackages = prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
        )
    }

    private fun loadGeneralSettings(): GeneralSettings {
        return GeneralSettings(
            dns = DnsSettings(
                provider = prefs.getString(KEY_DNS_PROVIDER, "Cloudflare (1.1.1.1)") ?: "Cloudflare (1.1.1.1)",
                primaryDns = prefs.getString(KEY_DNS_PRIMARY, "1.1.1.1") ?: "1.1.1.1",
                dohUrl = prefs.getString(KEY_DNS_DOH_URL, "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query",
                enableDoh = prefs.getBoolean(KEY_DNS_ENABLE_DOH, true)
            ),
            socksPort = prefs.getInt(KEY_SOCKS_PORT, 10808),
            httpPort = prefs.getInt(KEY_HTTP_PORT, 10809),
            allowLanSharing = prefs.getBoolean(KEY_ALLOW_LAN, false),
            killSwitchEnabled = prefs.getBoolean(KEY_KILL_SWITCH, false),
            autoConnectOnBoot = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            connectOnLaunch = prefs.getBoolean(KEY_CONNECT_ON_LAUNCH, false),
            batteryMonitorEnabled = prefs.getBoolean(KEY_BATTERY_MONITOR_ENABLED, true),
            batteryDrainThresholdPercent = prefs.getInt(KEY_BATTERY_DRAIN_THRESHOLD, 3)
        )
    }

    companion object {
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_WIZARD_COMPLETED = "wizard_completed"

        private const val KEY_FRAG_ENABLED = "frag_enabled"
        private const val KEY_FRAG_LENGTH = "frag_length"
        private const val KEY_FRAG_INTERVAL = "frag_interval"
        private const val KEY_FRAG_PACKETS = "frag_packets"

        private const val KEY_NOISE_ENABLED = "noise_enabled"
        private const val KEY_NOISE_COUNT = "noise_count"
        private const val KEY_NOISE_PAYLOAD = "noise_payload"

        private const val KEY_WARP_ENABLED = "warp_enabled"
        private const val KEY_WARP_LICENSE = "warp_license"

        private const val KEY_TOR_ENABLED = "tor_enabled"
        private const val KEY_TOR_TYPE = "tor_type"

        private const val KEY_MTU_VALUE = "mtu_value"
        private const val KEY_MTU_AUTO = "mtu_auto"

        private const val KEY_ROUTING_MODE = "routing_mode"
        private const val KEY_SPLIT_TUNNEL_ENABLED = "split_tunnel_enabled"
        private const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"

        private const val KEY_DNS_PROVIDER = "dns_provider"
        private const val KEY_DNS_PRIMARY = "dns_primary"
        private const val KEY_DNS_DOH_URL = "dns_doh_url"
        private const val KEY_DNS_ENABLE_DOH = "dns_enable_doh"
        private const val KEY_SOCKS_PORT = "socks_port"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_ALLOW_LAN = "allow_lan"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_CONNECT_ON_LAUNCH = "connect_on_launch"
        private const val KEY_BATTERY_MONITOR_ENABLED = "battery_monitor_enabled"
        private const val KEY_BATTERY_DRAIN_THRESHOLD = "battery_drain_threshold"
    }
}
