package com.example.data

import com.example.model.ProtocolType
import com.example.model.ServerProfile

object DefaultServers {
    fun getInitialServers(): List<ServerProfile> = listOf(
        ServerProfile(
            id = "bpb-cf-worker-us",
            name = "BPB Freedom Edge (US East)",
            protocol = ProtocolType.CLOUDFLARE_WORKER,
            address = "bpb-worker.bpb-network.workers.dev",
            port = 443,
            uuid = "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
            serverName = "bpb-worker.bpb-network.workers.dev",
            network = "ws",
            security = "tls",
            path = "/bpb-tunnel-v2",
            countryCode = "US",
            pingMs = 124,
            isFavorite = true,
            isCustom = false
        ),
        ServerProfile(
            id = "vless-reality-de",
            name = "VLESS Reality Direct (Frankfurt)",
            protocol = ProtocolType.VLESS,
            address = "de.edge-stealth.net",
            port = 443,
            uuid = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
            flow = "xtls-rprx-vision",
            serverName = "www.microsoft.com",
            network = "tcp",
            security = "reality",
            publicKey = "j8X5_M3eXwR7pLk9q2N8v1YbT4zA6uF0oP7sD3iE9cA",
            shortId = "1a2b3c4d",
            fingerprint = "chrome",
            countryCode = "DE",
            pingMs = 88,
            isFavorite = true,
            isCustom = false
        ),
        ServerProfile(
            id = "warp-wireguard-anycast",
            name = "Cloudflare WARP+ Anti-DPI (Anycast)",
            protocol = ProtocolType.WARP,
            address = "162.159.192.1",
            port = 2408,
            uuid = "warp-client-session",
            serverName = "engage.cloudflareclient.com",
            network = "tcp",
            security = "tls",
            countryCode = "GLOBAL",
            pingMs = 45,
            isFavorite = true,
            isCustom = false
        ),
        ServerProfile(
            id = "trojan-ws-nl",
            name = "Trojan WebSocket Fronting (Amsterdam)",
            protocol = ProtocolType.TROJAN,
            address = "nl.cloud-front-proxy.org",
            port = 443,
            uuid = "trojan-bypass-key-2026",
            serverName = "nl.cloud-front-proxy.org",
            network = "ws",
            security = "tls",
            path = "/trojan-stream",
            countryCode = "NL",
            pingMs = 95,
            isCustom = false
        ),
        ServerProfile(
            id = "vmess-cf-sg",
            name = "VMess Cloudflare Anycast (Singapore)",
            protocol = ProtocolType.VMESS,
            address = "sg.anycast-stream.com",
            port = 443,
            uuid = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            alterId = 0,
            cipher = "auto",
            serverName = "sg.anycast-stream.com",
            network = "ws",
            security = "tls",
            path = "/vmess-path",
            countryCode = "SG",
            pingMs = 160,
            isCustom = false
        ),
        ServerProfile(
            id = "psiphon-freedom-exit",
            name = "Psiphon Obfuscated Tunnel (Global)",
            protocol = ProtocolType.PSIPHON,
            address = "psiphon.network.relay",
            port = 443,
            uuid = "psiphon-auto-tunnel",
            serverName = "psiphon.ca",
            network = "tcp",
            security = "tls",
            countryCode = "CA",
            pingMs = 185,
            isCustom = false
        )
    )
}
