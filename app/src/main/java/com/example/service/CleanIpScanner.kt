package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetSocketAddress
import java.net.Socket

data class CleanIpResult(
    val ip: String,
    val latencyMs: Long,
    val isReachable: Boolean,
    val ispLocation: String,
    val quality: IpQuality
)

enum class IpQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    UNREACHABLE
}

object CleanIpScanner {

    private val CANDIDATE_IPS = listOf(
        Pair("104.16.132.229", "Cloudflare Anycast (Edge US)"),
        Pair("172.67.182.115", "Cloudflare Anycast (Edge EU)"),
        Pair("104.21.32.18", "Cloudflare Anycast (Frankfurt)"),
        Pair("162.159.192.1", "Cloudflare WARP Anycast (Global)"),
        Pair("104.19.155.10", "Cloudflare Anycast (Amsterdam)"),
        Pair("172.67.74.88", "Cloudflare Anycast (London)"),
        Pair("104.24.112.5", "Cloudflare Anycast (Tokyo)"),
        Pair("188.114.96.2", "Cloudflare Anycast (Stockholm)"),
        Pair("141.101.121.34", "Cloudflare Anycast (Singapore)"),
        Pair("198.41.214.162", "Cloudflare Anycast (Zurich)")
    )

    fun scanCleanIps(): Flow<List<CleanIpResult>> = flow {
        val results = mutableListOf<CleanIpResult>()

        for ((ip, location) in CANDIDATE_IPS) {
            val start = System.currentTimeMillis()
            var reachable = false
            var rtt = 999L

            try {
                Socket().use { socket ->
                    socket.soTimeout = 1500
                    socket.connect(InetSocketAddress(ip, 443), 1500)
                    rtt = System.currentTimeMillis() - start
                    reachable = true
                }
            } catch (_: Exception) {
                reachable = false
                rtt = -1L
            }

            delay(50)

            val quality = when {
                !reachable -> IpQuality.UNREACHABLE
                rtt < 80 -> IpQuality.EXCELLENT
                rtt < 150 -> IpQuality.GOOD
                else -> IpQuality.FAIR
            }

            results.add(CleanIpResult(
                ip = ip,
                latencyMs = rtt,
                isReachable = reachable,
                ispLocation = location,
                quality = quality
            ))

            emit(results.sortedBy { if (it.isReachable) it.latencyMs else 9999L }.toList())
        }
    }.flowOn(Dispatchers.IO)
}
