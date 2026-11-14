package com.example.service

import com.example.model.FragmentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure-Kotlin local SOCKS5 & HTTP CONNECT proxy forwarder bound to the VPN's local inbound ports.
 * - Protects every upstream socket via [socketProtector] (`VpnService::protect`) to prevent routing loops.
 * - Applies real TLS ClientHello TCP segmentation when [FragmentConfig.enabled] is true.
 * - Tracks actual relayed RX/TX byte counters without synthetic simulation.
 */
class LocalProxyEngine(
    private val socketProtector: (Socket) -> Boolean = { true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socksServer: ServerSocket? = null
    private var httpServer: ServerSocket? = null
    private var socksAcceptJob: Job? = null
    private var httpAcceptJob: Job? = null

    val totalRxBytes = AtomicLong(0L)
    val totalTxBytes = AtomicLong(0L)

    @Volatile
    var activeSocksPort: Int = -1
        private set

    @Volatile
    var activeHttpPort: Int = -1
        private set

    fun start(
        socksPort: Int,
        httpPort: Int,
        allowLan: Boolean = false,
        fragmentConfig: FragmentConfig = FragmentConfig()
    ) {
        stop()
        val bindAddress = if (allowLan) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress()

        socksServer = openServerSocket(bindAddress, socksPort)
        activeSocksPort = socksServer?.localPort ?: -1

        httpServer = openServerSocket(bindAddress, httpPort)
        activeHttpPort = httpServer?.localPort ?: -1

        socksServer?.let { server ->
            socksAcceptJob = scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        launch { handleSocks5Client(client, fragmentConfig) }
                    } catch (_: Exception) {
                        if (server.isClosed) break
                    }
                }
            }
        }

        httpServer?.let { server ->
            httpAcceptJob = scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        launch { handleHttpClient(client, fragmentConfig) }
                    } catch (_: Exception) {
                        if (server.isClosed) break
                    }
                }
            }
        }
    }

    fun stop() {
        socksAcceptJob?.cancel()
        httpAcceptJob?.cancel()
        socksAcceptJob = null
        httpAcceptJob = null
        try {
            socksServer?.close()
        } catch (_: Exception) {
        }
        try {
            httpServer?.close()
        } catch (_: Exception) {
        }
        socksServer = null
        httpServer = null
        activeSocksPort = -1
        activeHttpPort = -1
    }

    private fun openServerSocket(bindAddress: InetAddress, preferredPort: Int): ServerSocket? {
        return try {
            ServerSocket(preferredPort, 64, bindAddress)
        } catch (_: Exception) {
            try {
                ServerSocket(0, 64, bindAddress)
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun handleSocks5Client(client: Socket, fragmentConfig: FragmentConfig) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15_000
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            // Greeting: VER, NMETHODS, METHODS...
            val ver = clientIn.read()
            val nMethods = clientIn.read()
            if (ver != 0x05 || nMethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nMethods)
            readFully(clientIn, methods)
            // Select NO AUTHENTICATION REQUIRED (0x00)
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // Request: VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT
            val reqHeader = ByteArray(4)
            readFully(clientIn, reqHeader)
            if (reqHeader[0].toInt() != 0x05 || reqHeader[1].toInt() != 0x01) {
                clientOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                client.close()
                return
            }

            val targetHost = when (reqHeader[3].toInt() and 0xFF) {
                0x01 -> {
                    val ipBytes = ByteArray(4)
                    readFully(clientIn, ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                0x03 -> {
                    val len = clientIn.read()
                    if (len <= 0) "" else {
                        val domain = ByteArray(len)
                        readFully(clientIn, domain)
                        String(domain, Charsets.UTF_8)
                    }
                }
                0x04 -> {
                    val ipBytes = ByteArray(16)
                    readFully(clientIn, ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                else -> ""
            }
            val portBytes = ByteArray(2)
            readFully(clientIn, portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            if (targetHost.isBlank() || targetPort <= 0) {
                clientOut.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                client.close()
                return
            }

            upstream = Socket()
            socketProtector(upstream)
            upstream.connect(InetSocketAddress(targetHost, targetPort), 5000)
            upstream.soTimeout = 15_000

            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            relayBidirectional(client, clientIn, clientOut, upstream, fragmentConfig)
        } catch (_: Exception) {
            try {
                client.close()
            } catch (_: Exception) {
            }
            try {
                upstream?.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleHttpClient(client: Socket, fragmentConfig: FragmentConfig) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15_000
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            val requestLine = readHttpLine(clientIn) ?: run {
                client.close()
                return
            }
            // Drain remaining request headers until blank line
            while (true) {
                val hdr = readHttpLine(clientIn) ?: break
                if (hdr.isEmpty()) break
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) {
                clientOut.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".toByteArray())
                clientOut.flush()
                client.close()
                return
            }

            val authority = parts[1]
            val hostPort = authority.split(":", limit = 2)
            val host = hostPort[0]
            val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

            upstream = Socket()
            socketProtector(upstream)
            upstream.connect(InetSocketAddress(host, port), 5000)
            upstream.soTimeout = 15_000

            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOut.flush()

            relayBidirectional(client, clientIn, clientOut, upstream, fragmentConfig)
        } catch (_: Exception) {
            try {
                client.close()
            } catch (_: Exception) {
            }
            try {
                upstream?.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun relayBidirectional(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        upstream: Socket,
        fragmentConfig: FragmentConfig
    ) {
        val upIn = upstream.getInputStream()
        val upOut = upstream.getOutputStream()

        val c2u = scope.launch {
            val buf = ByteArray(16 * 1024)
            var isFirstPacket = true
            try {
                while (true) {
                    val n = clientIn.read(buf)
                    if (n <= 0) break
                    if (isFirstPacket && fragmentConfig.enabled && isTlsClientHello(buf, n)) {
                        writeFragmented(upOut, buf, n, fragmentConfig)
                    } else {
                        upOut.write(buf, 0, n)
                        upOut.flush()
                    }
                    isFirstPacket = false
                    totalTxBytes.addAndGet(n.toLong())
                }
            } catch (_: Exception) {
            } finally {
                try {
                    upstream.shutdownOutput()
                } catch (_: Exception) {
                }
            }
        }

        val u2c = scope.launch {
            val buf = ByteArray(16 * 1024)
            try {
                while (true) {
                    val n = upIn.read(buf)
                    if (n <= 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                    totalRxBytes.addAndGet(n.toLong())
                }
            } catch (_: Exception) {
            } finally {
                try {
                    client.shutdownOutput()
                } catch (_: Exception) {
                }
            }
        }

        c2u.join()
        u2c.join()
        try {
            client.close()
        } catch (_: Exception) {
        }
        try {
            upstream.close()
        } catch (_: Exception) {
        }
    }

    private suspend fun writeFragmented(
        out: OutputStream,
        data: ByteArray,
        length: Int,
        config: FragmentConfig
    ) {
        val (minChunk, maxChunk) = parseRange(config.length, 10, 50)
        val (minDelay, maxDelay) = parseRange(config.interval, 5, 20)
        var offset = 0
        while (offset < length) {
            val chunk = (minChunk..maxChunk).random().coerceAtMost(length - offset).coerceAtLeast(1)
            out.write(data, offset, chunk)
            out.flush()
            offset += chunk
            if (offset < length && maxDelay > 0) {
                delay((minDelay..maxDelay).random().toLong())
            }
        }
    }

    private fun isTlsClientHello(buf: ByteArray, len: Int): Boolean {
        return len >= 6 &&
            buf[0].toInt() == 0x16 &&
            buf[1].toInt() == 0x03 &&
            buf[5].toInt() == 0x01
    }

    private fun parseRange(spec: String, defaultMin: Int, defaultMax: Int): Pair<Int, Int> {
        val parts = spec.split("-")
        val a = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: defaultMin
        val b = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: a.coerceAtLeast(defaultMax)
        return Pair(minOf(a, b).coerceAtLeast(1), maxOf(a, b).coerceAtLeast(1))
    }

    private fun readFully(input: InputStream, dest: ByteArray) {
        var offset = 0
        while (offset < dest.size) {
            val read = input.read(dest, offset, dest.size - offset)
            if (read == -1) throw IllegalStateException("Unexpected EOF")
            offset += read
        }
    }

    private fun readHttpLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prevCr = false
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                return sb.toString()
            }
            if (prevCr) sb.append('\r')
            if (b == '\r'.code) {
                prevCr = true
            } else {
                prevCr = false
                sb.append(b.toChar())
            }
        }
    }
}
