package com.example.model

enum class RoutingMode {
    GLOBAL_PROXY,
    BYPASS_LAN_AND_DOMESTIC,
    CUSTOM_RULES
}

enum class SplitTunnelMode {
    PROXY_SELECTED_ONLY,
    BYPASS_SELECTED_ONLY
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = false
)

data class RoutingSettings(
    val mode: RoutingMode = RoutingMode.BYPASS_LAN_AND_DOMESTIC,
    val splitTunnelEnabled: Boolean = false,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.BYPASS_SELECTED_ONLY,
    val selectedPackages: Set<String> = emptySet(),
    val directDomains: List<String> = listOf("localhost", "*.ir", "*.cn", "*.ru", "*.local"),
    val proxyDomains: List<String> = listOf("*.google.com", "*.youtube.com", "*.instagram.com", "*.twitter.com", "*.x.com", "*.telegram.org"),
    val blockDomains: List<String> = listOf("*.doubleclick.net", "*.adservice.google.com")
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel = LogLevel.INFO,
    val message: String
)

data class DnsSettings(
    val provider: String = "Cloudflare (1.1.1.1)", // Cloudflare, Google, AdGuard, Custom
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "1.0.0.1",
    val dohUrl: String = "https://1.1.1.1/dns-query",
    val enableDoh: Boolean = true
)

data class GeneralSettings(
    val dns: DnsSettings = DnsSettings(),
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val allowLanSharing: Boolean = false,
    val killSwitchEnabled: Boolean = false,
    val autoConnectOnBoot: Boolean = false,
    val testUrl: String = "https://www.google.com/generate_204"
)
