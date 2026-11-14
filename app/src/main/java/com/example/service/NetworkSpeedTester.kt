package com.example.service

import com.example.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SpeedTestStatus {
    PENDING,
    TESTING_PING,
    TESTING_BANDWIDTH,
    COMPLETED,
    FAILED
}

data class ServerSpeedResult(
    val serverId: String,
    val serverName: String,
    val protocolLabel: String,
    val address: String,
    val port: Int,
    val pingMs: Long = -1L,
    val jitterMs: Long = 0L,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val isReachable: Boolean = false,
    val status: SpeedTestStatus = SpeedTestStatus.PENDING,
    val error: String? = null
) {
    /**
     * Composite score ranking servers with lower latency and higher throughput first.
     */
    val score: Double
        get() = if (!isReachable || pingMs <= 0L) {
            -1_000_000.0
        } else {
            (downloadMbps * 12.0) + (uploadMbps * 4.0) - (pingMs * 0.35) - (jitterMs * 0.15)
        }
}

data class SpeedTestSessionState(
    val isRunning: Boolean = false,
    val currentServerName: String? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val results: List<ServerSpeedResult> = emptyList(),
    val fastestServerId: String? = null
)

data class SpeedTestConfig(
    val baseUrl: String = "https://speed.cloudflare.com/",
    val downloadUrl: String = "https://speed.cloudflare.com/__down?bytes=524288",
    val uploadUrl: String = "https://speed.cloudflare.com/__up",
    val httpLatencyUrl: String? = null,
    val uploadPayloadBytes: Int = 131_072, // 128 KB default upload burst
    val timeoutMs: Int = 2500,
    val pingSamples: Int = 3
)

/**
 * Network Speed Test module built on Kotlin Coroutines + Retrofit + OkHttp.
 * Measures real TCP/HTTP ping, jitter, download Mbps, and upload Mbps so users
 * can select the fastest server before establishing the VPN tunnel.
 */
class NetworkSpeedTester(
    private val config: SpeedTestConfig = SpeedTestConfig(),
    client: OkHttpClient = defaultOkHttpClient(config.timeoutMs)
) {

    private val api: SpeedTestApi = Retrofit.Builder()
        .baseUrl(config.baseUrl)
        .client(client)
        .build()
        .create(SpeedTestApi::class.java)

    /**
     * Runs a sequential per-server benchmark stream across [servers], emitting live progress
     * and continuously ranking the fastest reachable server.
     */
    fun benchmarkServers(servers: List<ServerProfile>): Flow<SpeedTestSessionState> = flow {
        if (servers.isEmpty()) {
            emit(SpeedTestSessionState(isRunning = false, totalCount = 0))
            return@flow
        }

        val resultMap = servers.associate { server ->
            server.id to ServerSpeedResult(
                serverId = server.id,
                serverName = server.name,
                protocolLabel = server.protocol.name.replace("_", " "),
                address = server.customCleanIp.ifBlank { server.address },
                port = server.port,
                status = SpeedTestStatus.PENDING
            )
        }.toMutableMap()

        emit(
            SpeedTestSessionState(
                isRunning = true,
                totalCount = servers.size,
                results = servers.map { resultMap[it.id]!! }
            )
        )

        var completed = 0
        for (server in servers) {
            val targetHost = server.customCleanIp.ifBlank { server.address }
            resultMap[server.id] = resultMap[server.id]!!.copy(status = SpeedTestStatus.TESTING_PING)
            emit(snapshot(resultMap, servers, true, server.name, completed))

            // 1. Measure real RTT & jitter
            val (pingMs, jitterMs, httpRttMs) = measureLatencySamples(targetHost, server.port)
            if (pingMs <= 0L) {
                resultMap[server.id] = resultMap[server.id]!!.copy(
                    pingMs = -1L,
                    isReachable = false,
                    status = SpeedTestStatus.FAILED,
                    error = "Host unreachable or timed out"
                )
                completed++
                emit(snapshot(resultMap, servers, true, server.name, completed))
                continue
            }

            resultMap[server.id] = resultMap[server.id]!!.copy(
                pingMs = pingMs,
                jitterMs = jitterMs,
                isReachable = true,
                status = SpeedTestStatus.TESTING_BANDWIDTH
            )
            emit(snapshot(resultMap, servers, true, server.name, completed))

            // 2. Measure download & upload throughput via Retrofit
            val downMbps = measureDownloadMbps()
            val upMbps = measureUploadMbps()

            val effectivePing = if (httpRttMs > 0L) minOf(pingMs, httpRttMs) else pingMs
            resultMap[server.id] = resultMap[server.id]!!.copy(
                pingMs = effectivePing,
                jitterMs = jitterMs,
                downloadMbps = downMbps,
                uploadMbps = upMbps,
                isReachable = true,
                status = SpeedTestStatus.COMPLETED
            )
            completed++
            emit(snapshot(resultMap, servers, true, server.name, completed))
        }

        emit(snapshot(resultMap, servers, false, null, completed))
    }.flowOn(Dispatchers.IO)

    /**
     * Benchmarks a single server and returns its [ServerSpeedResult].
     */
    suspend fun testSingleServer(server: ServerProfile): ServerSpeedResult = withContext(Dispatchers.IO) {
        val targetHost = server.customCleanIp.ifBlank { server.address }
        val (pingMs, jitterMs, httpRttMs) = measureLatencySamples(targetHost, server.port)
        if (pingMs <= 0L) {
            return@withContext ServerSpeedResult(
                serverId = server.id,
                serverName = server.name,
                protocolLabel = server.protocol.name.replace("_", " "),
                address = targetHost,
                port = server.port,
                pingMs = -1L,
                isReachable = false,
                status = SpeedTestStatus.FAILED,
                error = "Endpoint unreachable"
            )
        }
        val dl = measureDownloadMbps()
        val ul = measureUploadMbps()
        ServerSpeedResult(
            serverId = server.id,
            serverName = server.name,
            protocolLabel = server.protocol.name.replace("_", " "),
            address = targetHost,
            port = server.port,
            pingMs = if (httpRttMs > 0L) minOf(pingMs, httpRttMs) else pingMs,
            jitterMs = jitterMs,
            downloadMbps = dl,
            uploadMbps = ul,
            isReachable = true,
            status = SpeedTestStatus.COMPLETED
        )
    }

    private suspend fun measureLatencySamples(host: String, port: Int): Triple<Long, Long, Long> {
        val tcpSamples = mutableListOf<Long>()
        repeat(config.pingSamples.coerceAtLeast(1)) {
            val rtt = tcpConnectRttMs(host, port, config.timeoutMs)
            if (rtt > 0L) tcpSamples.add(rtt)
        }

        if (tcpSamples.isEmpty()) return Triple(-1L, 0L, -1L)

        var httpRtt = -1L
        val httpUrl = config.httpLatencyUrl
        if (!httpUrl.isNullOrBlank()) {
            try {
                val startNs = System.nanoTime()
                val resp = api.probeLatency(httpUrl)
                if (resp.isSuccessful || resp.code() in 200..499) {
                    httpRtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                }
            } catch (_: Exception) {
                // Keep TCP sample
            }
        }

        val avgPing = tcpSamples.average().roundToLong().coerceAtLeast(1L)
        val jitter = if (tcpSamples.size > 1) {
            tcpSamples.map { abs(it - avgPing) }.average().roundToLong()
        } else {
            0L
        }
        return Triple(avgPing, jitter, httpRtt)
    }

    private suspend fun measureDownloadMbps(): Double {
        return try {
            val startNs = System.nanoTime()
            val body = api.downloadChunk(config.downloadUrl)
            var totalBytes = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                }
            }
            val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1_000_000L)
            val elapsedSeconds = elapsedNs / 1_000_000_000.0
            ((totalBytes * 8.0) / (elapsedSeconds * 1_000_000.0)).coerceAtLeast(0.01)
        } catch (_: Exception) {
            0.0
        }
    }

    private suspend fun measureUploadMbps(): Double {
        return try {
            val payloadBytes = ByteArray(config.uploadPayloadBytes) { 0x42 }
            val reqBody = payloadBytes.toRequestBody("application/octet-stream".toMediaType())
            val startNs = System.nanoTime()
            val response = api.uploadChunk(config.uploadUrl, reqBody)
            val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1_000_000L)
            if (!response.isSuccessful) return 0.0
            val elapsedSeconds = elapsedNs / 1_000_000_000.0
            ((payloadBytes.size * 8.0) / (elapsedSeconds * 1_000_000.0)).coerceAtLeast(0.01)
        } catch (_: Exception) {
            0.0
        }
    }

    private fun tcpConnectRttMs(host: String, port: Int, timeoutMs: Int): Long {
        return try {
            val startNs = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun snapshot(
        resultMap: Map<String, ServerSpeedResult>,
        servers: List<ServerProfile>,
        isRunning: Boolean,
        currentName: String?,
        completedCount: Int
    ): SpeedTestSessionState {
        val ordered = servers.map { resultMap[it.id]!! }
            .sortedWith(
                compareByDescending<ServerSpeedResult> { it.isReachable }
                    .thenByDescending { it.score }
            )
        val fastest = ordered.firstOrNull { it.isReachable && it.pingMs > 0L }?.serverId
        return SpeedTestSessionState(
            isRunning = isRunning,
            currentServerName = currentName,
            completedCount = completedCount,
            totalCount = servers.size,
            results = ordered,
            fastestServerId = fastest
        )
    }

    companion object {
        fun defaultOkHttpClient(timeoutMs: Int = 3000): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong() * 3, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs.toLong() * 3, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
