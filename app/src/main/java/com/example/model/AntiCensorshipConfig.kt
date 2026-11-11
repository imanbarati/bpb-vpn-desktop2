package com.example.model

data class FragmentConfig(
    val enabled: Boolean = true,
    val length: String = "10-50",
    val interval: String = "10-30",
    val packets: String = "tlshello"
)

data class NoiseConfig(
    val enabled: Boolean = true,
    val packetCount: Int = 12,
    val minDelayMs: Int = 5,
    val maxDelayMs: Int = 20,
    val payloadType: String = "quic" // quic, rand, str
)

data class WarpConfig(
    val enabled: Boolean = false,
    val warpPlusLicense: String = "",
    val isWarpPlus: Boolean = false,
    val reserved: String = "0,0,0",
    val privateKey: String = "",
    val peerPublicKey: String = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
)

data class TorBridgeConfig(
    val enabled: Boolean = false,
    val bridgeType: String = "Snowflake", // Snowflake, obfs4, WebTunnel
    val customBridgeLines: String = ""
)

data class MtuConfig(
    val mtu: Int = 1420,
    val autoDetect: Boolean = true,
    val lastDiscoveredMtu: Int = 1420,
    val lastDiscoveredLossPercent: Float = 0f
)

data class ChainProxyConfig(
    val enabled: Boolean = false,
    val relayServerId: String = ""
)

data class AntiCensorshipSettings(
    val fragment: FragmentConfig = FragmentConfig(),
    val noise: NoiseConfig = NoiseConfig(),
    val warp: WarpConfig = WarpConfig(),
    val tor: TorBridgeConfig = TorBridgeConfig(),
    val mtu: MtuConfig = MtuConfig(),
    val chainProxy: ChainProxyConfig = ChainProxyConfig()
)
