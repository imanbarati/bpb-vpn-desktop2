package com.example.service

import android.net.Uri
import android.util.Base64
import com.example.model.ProtocolType
import com.example.model.ServerProfile
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UriParser {

    fun parse(rawUri: String): ServerProfile? {
        val trimmed = rawUri.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
            trimmed.startsWith("warp://", ignoreCase = true) -> parseWarp(trimmed)
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> parseWorkerUrl(trimmed)
            else -> null
        }
    }

    private fun parseVless(uriStr: String): ServerProfile? {
        return try {
            val hashSplit = uriStr.split("#", limit = 2)
            val name = if (hashSplit.size > 1) decode(hashSplit[1]) else "VLESS Node"
            val uri = Uri.parse(hashSplit[0])

            val userInfo = uri.userInfo ?: ""
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443

            val type = uri.getQueryParameter("type") ?: "ws"
            val security = uri.getQueryParameter("security") ?: "tls"
            val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("serverName") ?: host
            val path = uri.getQueryParameter("path") ?: "/"
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val flow = uri.getQueryParameter("flow") ?: ""
            val fp = uri.getQueryParameter("fp") ?: "chrome"

            ServerProfile(
                name = name,
                protocol = ProtocolType.VLESS,
                address = host,
                port = port,
                uuid = userInfo,
                network = type,
                security = security,
                serverName = sni,
                path = path,
                publicKey = pbk,
                shortId = sid,
                flow = flow,
                fingerprint = fp,
                isCustom = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTrojan(uriStr: String): ServerProfile? {
        return try {
            val hashSplit = uriStr.split("#", limit = 2)
            val name = if (hashSplit.size > 1) decode(hashSplit[1]) else "Trojan Node"
            val uri = Uri.parse(hashSplit[0])

            val password = uri.userInfo ?: ""
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443

            val type = uri.getQueryParameter("type") ?: "ws"
            val security = uri.getQueryParameter("security") ?: "tls"
            val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
            val path = uri.getQueryParameter("path") ?: "/"

            ServerProfile(
                name = name,
                protocol = ProtocolType.TROJAN,
                address = host,
                port = port,
                uuid = password,
                network = type,
                security = security,
                serverName = sni,
                path = path,
                isCustom = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVmess(uriStr: String): ServerProfile? {
        return try {
            val rawB64 = uriStr.substringAfter("vmess://").trim()
            val decodedJsonStr = String(Base64.decode(rawB64, Base64.DEFAULT), StandardCharsets.UTF_8)
            val json = JSONObject(decodedJsonStr)

            val name = json.optString("ps", "VMess Node")
            val host = json.optString("add", "")
            val port = json.optInt("port", 443)
            val id = json.optString("id", "")
            val aid = json.optInt("aid", 0)
            val net = json.optString("net", "ws")
            val path = json.optString("path", "/")
            val hostHeader = json.optString("host", host)
            val tls = json.optString("tls", "tls")
            val sni = json.optString("sni", hostHeader)

            ServerProfile(
                name = name,
                protocol = ProtocolType.VMESS,
                address = host,
                port = port,
                uuid = id,
                alterId = aid,
                network = net,
                security = if (tls.isNotEmpty()) tls else "none",
                serverName = sni,
                path = path,
                isCustom = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseShadowsocks(uriStr: String): ServerProfile? {
        return try {
            val hashSplit = uriStr.split("#", limit = 2)
            val name = if (hashSplit.size > 1) decode(hashSplit[1]) else "Shadowsocks Node"
            val afterScheme = hashSplit[0].removePrefix("ss://")

            if (afterScheme.contains("@")) {
                val parts = afterScheme.split("@", limit = 2)
                val methodPassDecoded = try {
                    String(Base64.decode(parts[0], Base64.DEFAULT), StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    parts[0]
                }
                val methodPass = methodPassDecoded.split(":", limit = 2)
                val method = methodPass[0]
                val password = if (methodPass.size > 1) methodPass[1] else ""

                val hostPort = parts[1].split(":", limit = 2)
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 8388 else 8388

                ServerProfile(
                    name = name,
                    protocol = ProtocolType.SHADOWSOCKS,
                    address = host,
                    port = port,
                    uuid = password,
                    cipher = method,
                    network = "tcp",
                    security = "none",
                    isCustom = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseWarp(uriStr: String): ServerProfile {
        return ServerProfile(
            name = "WARP Custom Endpoint",
            protocol = ProtocolType.WARP,
            address = "162.159.192.1",
            port = 2408,
            serverName = "engage.cloudflareclient.com",
            isCustom = true
        )
    }

    private fun parseWorkerUrl(url: String): ServerProfile? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            ServerProfile(
                name = "Cloudflare Worker ($host)",
                protocol = ProtocolType.CLOUDFLARE_WORKER,
                address = host,
                port = 443,
                uuid = "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
                serverName = host,
                path = if (uri.path.isNullOrEmpty()) "/bpb-tunnel" else uri.path!!,
                network = "ws",
                security = "tls",
                isCustom = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(s: String): String {
        return try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }
    }
}
