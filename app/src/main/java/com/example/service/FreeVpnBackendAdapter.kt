package com.example.service

import com.example.model.AntiCensorshipSettings
import com.example.model.ProtocolType
import com.example.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ln
import kotlin.math.sqrt

data class FirefoxVpnAccount(
    val id: String,
    val label: String,
    val bearerToken: String,
    val monthlyQuotaBytes: Long = 50L * 1024L * 1024L * 1024L, // 50 GB free tier
    val usedBytes: Long = 0L,
    val isRateLimited: Boolean = false
) {
    val remainingBytes: Long
        get() = (monthlyQuotaBytes - usedBytes).coerceAtLeast(0L)

    val hasQuota: Boolean
        get() = !isRateLimited && remainingBytes > 0L
}

/**
 * Multi-account quota & rotation pool inspired by firefox-vpn-client (Fastly H2/H3 proxy relays).
 */
class FirefoxVpnAccountPool(
    initialAccounts: List<FirefoxVpnAccount> = defaultFreeTierAccounts()
) {
    private val accounts = initialAccounts.toMutableList()
    private var activeIndex = 0

    @Synchronized
    fun acquireActiveAccount(): FirefoxVpnAccount? {
        if (accounts.isEmpty()) return null
        for (offset in accounts.indices) {
            val idx = (activeIndex + offset) % accounts.size
            val candidate = accounts[idx]
            if (candidate.hasQuota) {
                activeIndex = idx
                return candidate
            }
        }
        return null
    }

    @Synchronized
    fun recordUsage(accountId: String, deltaBytes: Long) {
        val idx = accounts.indexOfFirst { it.id == accountId }
        if (idx >= 0) {
            val acc = accounts[idx]
            accounts[idx] = acc.copy(usedBytes = acc.usedBytes + deltaBytes.coerceAtLeast(0L))
        }
    }

    @Synchronized
    fun markRateLimitedAndRotate(accountId: String): FirefoxVpnAccount? {
        val idx = accounts.indexOfFirst { it.id == accountId }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(isRateLimited = true)
        }
        return acquireActiveAccount()
    }

    @Synchronized
    fun resetAccounts(newAccounts: List<FirefoxVpnAccount>) {
        accounts.clear()
        accounts.addAll(newAccounts)
        activeIndex = 0
    }

    @Synchronized
    fun getAllAccounts(): List<FirefoxVpnAccount> = accounts.toList()

    companion object {
        fun defaultFreeTierAccounts(): List<FirefoxVpnAccount> = listOf(
            FirefoxVpnAccount(
                id = "fxa-slot-1",
                label = "Firefox Free Slot #1 (50 GB)",
                bearerToken = "fxa-free-tier-slot-1"
            ),
            FirefoxVpnAccount(
                id = "fxa-slot-2",
                label = "Firefox Free Slot #2 (50 GB)",
                bearerToken = "fxa-free-tier-slot-2"
            )
        )
    }
}

enum class TunnelPolicyArm(
    val label: String,
    val fragmentLength: String,
    val fragmentInterval: String,
    val noisePackets: Int,
    val transport: String
) {
    FRAGMENT_SMALL_H2("TLS-Frag Small + H2", "10-30", "10-20", 8, "h2"),
    FRAGMENT_MEDIUM_WS("TLS-Frag Medium + WS", "20-60", "15-35", 12, "ws"),
    NOISE_QUIC_H3("Noise Burst + QUIC/H3", "15-45", "10-25", 18, "h3"),
    REVERSE_RELAY_FALLBACK("Reverse Bridge Relay", "8-24", "5-15", 16, "ws")
}

/**
 * Lightweight on-device UCB1/contextual bandit policy engine for reverse-tunnel strategy tuning
 * (foundation for compact 1B-class edge policy models).
 */
class AdaptiveReverseTunnelPolicy {
    private val pulls = mutableMapOf<TunnelPolicyArm, Int>().apply {
        TunnelPolicyArm.entries.forEach { put(it, 0) }
    }
    private val rewardSums = mutableMapOf<TunnelPolicyArm, Double>().apply {
        TunnelPolicyArm.entries.forEach { put(it, 0.0) }
    }

    @Synchronized
    fun selectOptimalArm(): TunnelPolicyArm {
        // Explore any untried arm first
        for (arm in TunnelPolicyArm.entries) {
            if ((pulls[arm] ?: 0) == 0) {
                return arm
            }
        }
        val totalPulls = pulls.values.sum().coerceAtLeast(1)
        return TunnelPolicyArm.entries.maxByOrNull { arm ->
            val n = pulls[arm] ?: 1
            val meanReward = (rewardSums[arm] ?: 0.0) / n
            val ucbBonus = sqrt((2.0 * ln(totalPulls.toDouble())) / n)
            meanReward + ucbBonus
        } ?: TunnelPolicyArm.FRAGMENT_MEDIUM_WS
    }

    @Synchronized
    fun recordOutcome(
        arm: TunnelPolicyArm,
        success: Boolean,
        rttMs: Long,
        throughputKbps: Double = 0.0
    ) {
        val reward = if (!success) {
            -1.0
        } else {
            val latencyScore = (1.0 - (rttMs.coerceIn(10L, 1000L) / 1000.0)).coerceIn(0.1, 1.0)
            val speedBonus = (throughputKbps / 4096.0).coerceIn(0.0, 0.5)
            latencyScore + speedBonus
        }
        pulls[arm] = (pulls[arm] ?: 0) + 1
        rewardSums[arm] = (rewardSums[arm] ?: 0.0) + reward
    }

    @Synchronized
    fun getAverageReward(arm: TunnelPolicyArm): Double {
        val count = pulls[arm] ?: 0
        return if (count == 0) 0.0 else (rewardSums[arm] ?: 0.0) / count
    }

    @Synchronized
    fun applyArmToSettings(
        arm: TunnelPolicyArm,
        base: AntiCensorshipSettings
    ): AntiCensorshipSettings {
        return base.copy(
            fragment = base.fragment.copy(
                enabled = true,
                length = arm.fragmentLength,
                interval = arm.fragmentInterval
            ),
            noise = base.noise.copy(
                enabled = true,
                packetCount = arm.noisePackets
            )
        )
    }

    @Synchronized
    fun reset() {
        TunnelPolicyArm.entries.forEach {
            pulls[it] = 0
            rewardSums[it] = 0.0
        }
    }
}

data class BackendProbeResult(
    val reachable: Boolean,
    val rttMs: Long,
    val protocolUsed: ProtocolType,
    val resolvedAccountId: String? = null,
    val selectedArm: TunnelPolicyArm? = null,
    val serverBanner: String? = null,
    val error: String? = null
)

object FreeVpnBackendAdapter {

    val firefoxAccountPool = FirefoxVpnAccountPool()
    val reverseTunnelPolicy = AdaptiveReverseTunnelPolicy()

    /**
     * Performs a real TCP socket connection (and lightweight protocol handshake when applicable)
     * against the configured backend endpoint.
     */
    suspend fun probeAndPrepareBackend(
        server: ServerProfile,
        timeoutMs: Int = 1800
    ): BackendProbeResult = withContext(Dispatchers.IO) {
        val targetHost = server.customCleanIp.ifBlank { server.address }
        var accountId: String? = null
        var chosenArm: TunnelPolicyArm? = null

        if (server.protocol == ProtocolType.FIREFOX_VPN) {
            val account = firefoxAccountPool.acquireActiveAccount()
                ?: return@withContext BackendProbeResult(
                    reachable = false,
                    rttMs = -1L,
                    protocolUsed = server.protocol,
                    error = "All Firefox VPN free-tier accounts exhausted"
                )
            accountId = account.id
        }

        if (server.protocol == ProtocolType.REVERSE_TUNNEL) {
            chosenArm = reverseTunnelPolicy.selectOptimalArm()
        }

        val start = System.currentTimeMillis()
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetHost, server.port), timeoutMs)
                socket.soTimeout = timeoutMs

                var banner: String? = null
                if (server.protocol == ProtocolType.FIREFOX_VPN || server.protocol == ProtocolType.REVERSE_TUNNEL) {
                    val out = socket.getOutputStream()
                    val probeReq = when (server.protocol) {
                        ProtocolType.FIREFOX_VPN ->
                            "CONNECT ${server.address}:${server.port} HTTP/1.1\r\nHost: ${server.serverName.ifEmpty { server.address }}\r\nProxy-Authorization: Bearer ${accountId ?: "fxa"}\r\n\r\n"
                        ProtocolType.REVERSE_TUNNEL -> {
                            val revPath = if (server.path.isBlank() || server.path == "/") "/reverse-bridge" else server.path
                            "GET $revPath HTTP/1.1\r\nHost: ${server.serverName.ifEmpty { server.address }}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nX-Adaptive-Arm: ${chosenArm?.name ?: "DEFAULT"}\r\n\r\n"
                        }
                        else -> ""
                    }
                    if (probeReq.isNotEmpty()) {
                        out.write(probeReq.toByteArray(Charsets.UTF_8))
                        out.flush()
                        // Non-blocking read of initial response line if server immediately responds
                        if (socket.getInputStream().available() > 0 || server.address == "127.0.0.1" || server.address == "localhost") {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            banner = reader.readLine()
                        }
                    }
                }

                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (chosenArm != null) {
                    reverseTunnelPolicy.recordOutcome(chosenArm, success = true, rttMs = rtt, throughputKbps = 2048.0)
                }

                BackendProbeResult(
                    reachable = true,
                    rttMs = rtt,
                    protocolUsed = server.protocol,
                    resolvedAccountId = accountId,
                    selectedArm = chosenArm,
                    serverBanner = banner
                )
            }
        } catch (e: Exception) {
            if (chosenArm != null) {
                reverseTunnelPolicy.recordOutcome(chosenArm, success = false, rttMs = -1L)
            }
            BackendProbeResult(
                reachable = false,
                rttMs = -1L,
                protocolUsed = server.protocol,
                resolvedAccountId = accountId,
                selectedArm = chosenArm,
                error = e.localizedMessage ?: "Connection refused or timed out"
            )
        }
    }
}
