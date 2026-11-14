package com.example.service

import com.example.model.AntiCensorshipSettings
import com.example.model.GeneralSettings
import com.example.model.ProtocolType
import com.example.model.RoutingMode
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import org.json.JSONArray
import org.json.JSONObject

object ConfigGenerator {

    fun generateXrayConfig(
        server: ServerProfile,
        antiCensorship: AntiCensorshipSettings,
        routing: RoutingSettings,
        general: GeneralSettings
    ): String {
        val root = JSONObject()

        // 1. Log configuration
        val log = JSONObject().apply {
            put("loglevel", "warning")
            put("access", "")
            put("error", "")
        }
        root.put("log", log)

        // 2. Inbounds (SOCKS5 & HTTP local proxy)
        val inbounds = JSONArray()
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", general.socksPort)
            put("listen", if (general.allowLanSharing) "0.0.0.0" else "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                })
            })
        })

        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", general.httpPort)
            put("listen", if (general.allowLanSharing) "0.0.0.0" else "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject())
        })
        root.put("inbounds", inbounds)

        // 3. Outbounds
        val outbounds = JSONArray()

        // Primary Proxy Outbound
        val proxyOutbound = buildPrimaryOutbound(server, antiCensorship)
        outbounds.put(proxyOutbound)

        // Direct Outbound
        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("domainStrategy", "UseIP")
            })
        }
        outbounds.put(directOutbound)

        // Fragment Dialer Outbound (referenced by sockopt.dialerProxy)
        if (antiCensorship.fragment.enabled) {
            outbounds.put(JSONObject().apply {
                put("tag", "fragment-dialer")
                put("protocol", "freedom")
                put("settings", JSONObject().apply {
                    put("domainStrategy", "UseIP")
                    put("fragment", JSONObject().apply {
                        put("packets", antiCensorship.fragment.packets)
                        put("length", antiCensorship.fragment.length)
                        put("interval", antiCensorship.fragment.interval)
                    })
                })
            })
        }

        // Block Outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply {
                    put("type", "http")
                })
            })
        })

        // Cloudflare WARP Outbound (if WARP enabled as chained hop or fallback)
        if (antiCensorship.warp.enabled) {
            outbounds.put(JSONObject().apply {
                put("tag", "warp-exit")
                put("protocol", "wireguard")
                put("settings", JSONObject().apply {
                    put("secretKey", antiCensorship.warp.privateKey.ifEmpty { "eE9Qf...auto" })
                    put("address", JSONArray().put("172.16.0.2/32").put("2606:4700:110:8735:669e:2c28:7715:7bc0/128"))
                    put("peers", JSONArray().put(JSONObject().apply {
                        put("publicKey", antiCensorship.warp.peerPublicKey)
                        put("endpoint", "162.159.192.1:2408")
                        put("keepAlive", 25)
                    }))
                    put("reserved", JSONArray().apply {
                        antiCensorship.warp.reserved.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { put(it) }
                    })
                })
            })
        }

        root.put("outbounds", outbounds)

        // 4. DNS configuration
        val dnsObj = JSONObject().apply {
            val servers = JSONArray()
            if (general.dns.enableDoh) {
                servers.put(JSONObject().apply {
                    put("address", general.dns.dohUrl)
                    put("domains", JSONArray().put("domain:google.com").put("domain:youtube.com"))
                })
            }
            servers.put(general.dns.primaryDns)
            servers.put(general.dns.secondaryDns)
            put("servers", servers)
        }
        root.put("dns", dnsObj)

        // 5. Routing configuration
        val routingObj = JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            val rules = JSONArray()

            // Block ads
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "block")
                put("domain", JSONArray().apply {
                    routing.blockDomains.forEach { put(it) }
                    put("geosite:category-ads-all")
                })
            })

            // Bypass LAN
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "direct")
                put("ip", JSONArray().apply {
                    put("geoip:private")
                    put("127.0.0.1/32")
                    put("192.168.0.0/16")
                    put("10.0.0.0/8")
                })
            })

            // Bypass domestic if selected
            if (routing.mode == RoutingMode.BYPASS_LAN_AND_DOMESTIC) {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("domain", JSONArray().apply {
                        routing.directDomains.forEach { put(it) }
                        put("geosite:cn")
                        put("geosite:category-ir")
                        put("geosite:category-ru")
                    })
                    put("ip", JSONArray().apply {
                        put("geoip:cn")
                        put("geoip:ir")
                        put("geoip:ru")
                    })
                })
            }

            // Proxy target
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "proxy")
                put("network", "tcp,udp")
            })

            put("rules", rules)
        }
        root.put("routing", routingObj)

        // 6. Reverse Tunnel topology (when ProtocolType.REVERSE_TUNNEL is active)
        if (server.protocol == ProtocolType.REVERSE_TUNNEL) {
            root.put("reverse", JSONObject().apply {
                put("bridges", JSONArray().put(JSONObject().apply {
                    put("tag", "reverse-bridge")
                    put("domain", server.serverName.ifEmpty { server.address })
                }))
            })
        }

        return root.toString(2)
    }

    private fun buildPrimaryOutbound(server: ServerProfile, antiCensorship: AntiCensorshipSettings): JSONObject {
        val outbound = JSONObject().apply {
            put("tag", "proxy")
        }

        val targetAddress = if (server.customCleanIp.isNotBlank()) server.customCleanIp else server.address

        when (server.protocol) {
            ProtocolType.VLESS, ProtocolType.CLOUDFLARE_WORKER -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", server.uuid.ifEmpty { "e3b0c442-98fc-1c14-9afb-4c8996fb9242" })
                            put("encryption", "none")
                            if (server.flow.isNotBlank()) put("flow", server.flow)
                        }))
                    }))
                })
            }
            ProtocolType.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", server.uuid)
                            put("alterId", server.alterId)
                            put("security", server.cipher)
                        }))
                    }))
                })
            }
            ProtocolType.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        put("password", server.uuid)
                    }))
                })
            }
            ProtocolType.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        put("method", server.cipher.ifEmpty { "aes-128-gcm" })
                        put("password", server.uuid)
                    }))
                })
            }
            ProtocolType.WARP -> {
                outbound.put("protocol", "wireguard")
                outbound.put("settings", JSONObject().apply {
                    put("secretKey", "aGFzaC1wcml2YXRlLWtleS0yMDI2")
                    put("address", JSONArray().put("172.16.0.2/32"))
                    put("peers", JSONArray().put(JSONObject().apply {
                        put("publicKey", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=")
                        put("endpoint", "$targetAddress:${server.port}")
                    }))
                })
            }
            ProtocolType.PSIPHON -> {
                outbound.put("protocol", "socks")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", "127.0.0.1")
                        put("port", 10810) // Psiphon local socks exit
                    }))
                })
            }
            ProtocolType.FIREFOX_VPN -> {
                outbound.put("protocol", "http")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        if (server.uuid.isNotBlank()) {
                            put("users", JSONArray().put(JSONObject().apply {
                                put("user", "fxvpn")
                                put("pass", server.uuid)
                            }))
                        }
                    }))
                })
            }
            ProtocolType.REVERSE_TUNNEL -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", targetAddress)
                        put("port", server.port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", server.uuid.ifEmpty { "revtun-policy-o4-edge" })
                            put("encryption", "none")
                        }))
                    }))
                })
            }
        }

        // Stream Settings
        val streamSettings = JSONObject()
        streamSettings.put("network", if (server.protocol == ProtocolType.FIREFOX_VPN) "h2" else server.network)
        streamSettings.put("security", server.security)

        if (server.network == "ws") {
            streamSettings.put("wsSettings", JSONObject().apply {
                put("path", server.path.ifEmpty { "/" })
                put("headers", JSONObject().apply {
                    put("Host", server.serverName.ifEmpty { server.address })
                })
            })
        } else if (server.network == "grpc") {
            streamSettings.put("grpcSettings", JSONObject().apply {
                put("serviceName", server.path.removePrefix("/"))
            })
        } else if (server.network == "h2" || server.protocol == ProtocolType.FIREFOX_VPN) {
            streamSettings.put("httpSettings", JSONObject().apply {
                put("path", server.path.ifEmpty { "/fxvpn-h3-tunnel" })
                put("host", JSONArray().put(server.serverName.ifEmpty { server.address }))
            })
        }

        if (server.security == "tls") {
            streamSettings.put("tlsSettings", JSONObject().apply {
                put("serverName", server.serverName.ifEmpty { server.address })
                put("fingerprint", server.fingerprint)
                if (server.protocol == ProtocolType.FIREFOX_VPN) {
                    put("alpn", JSONArray().put("h3").put("h2").put("http/1.1"))
                }
                put("allowInsecure", false)
            })
        } else if (server.security == "reality") {
            streamSettings.put("realitySettings", JSONObject().apply {
                put("serverName", server.serverName.ifEmpty { "www.microsoft.com" })
                put("fingerprint", server.fingerprint)
                put("publicKey", server.publicKey)
                put("shortId", server.shortId)
                put("spiderX", "")
            })
        }

        // Sockopt with Anti-Censorship TLS Fragment & Noise
        val sockopt = JSONObject()
        sockopt.put("tcpKeepAliveInterval", 15)
        if (antiCensorship.fragment.enabled) {
            sockopt.put("dialerProxy", "fragment-dialer")
        }
        streamSettings.put("sockopt", sockopt)

        outbound.put("streamSettings", streamSettings)
        return outbound
    }
}
