package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.model.FragmentConfig
import com.example.model.GeneralSettings
import com.example.model.ProtocolType
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import com.example.model.SplitTunnelMode
import com.example.service.BpbVpnService
import com.example.service.LocalProxyEngine
import com.example.service.NetworkSpeedTester
import com.example.service.QrCodeDecoder
import com.example.service.ServerSpeedResult
import com.example.service.SpeedTestConfig
import com.example.service.SpeedTestSessionState
import com.example.service.SpeedTestStatus
import com.example.service.UriParser
import com.example.ui.dialogs.QrScannerDialog
import com.example.ui.dialogs.SpeedTestDialog
import com.example.ui.dialogs.VpnConsentDialog
import com.example.ui.screens.ServersScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrSpeedTestStrictVpnTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `qr decoder round-trips VLESS and VMess links from bitmap and image uri`() = runBlocking {
        val vlessUri = "vless://11111111-2222-3333-4444-555555555555@edge.bpb-example.net:443?type=ws&security=tls&sni=edge.bpb-example.net&path=%2Fws#QR-VLESS-Node"
        val vlessBitmap = QrCodeDecoder.generateQrBitmap(vlessUri, sizePx = 320)
        val decodedVless = QrCodeDecoder.decodeFromBitmap(vlessBitmap)
        assertEquals(vlessUri, decodedVless)

        val extractedVless = QrCodeDecoder.extractSupportedConfigUri("Imported config:\n$decodedVless")
        assertEquals(vlessUri, extractedVless)
        val parsedVless = UriParser.parse(extractedVless!!)
        assertNotNull(parsedVless)
        assertEquals(ProtocolType.VLESS, parsedVless?.protocol)
        assertEquals("QR-VLESS-Node", parsedVless?.name)
        assertEquals("edge.bpb-example.net", parsedVless?.address)

        // Test VMess JSON QR saved as a PNG file Uri (Photo Picker path)
        val vmessJson = """{"v":"2","ps":"QR-VMess-Node","add":"vmess.bpb-example.net","port":8443,"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","aid":0,"net":"ws","path":"/vmess","tls":"tls","sni":"vmess.bpb-example.net"}"""
        val vmessUri = "vmess://" + Base64.encodeToString(vmessJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val vmessBitmap = QrCodeDecoder.generateQrBitmap(vmessUri, sizePx = 320)

        val tempFile = File.createTempFile("qr_screen_", ".png", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                vmessBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val decodedFromUri = QrCodeDecoder.decodeFromUri(context, Uri.fromFile(tempFile))
            assertEquals(vmessUri, decodedFromUri)
            val parsedVmess = UriParser.parse(decodedFromUri!!)
            assertNotNull(parsedVmess)
            assertEquals(ProtocolType.VMESS, parsedVmess?.protocol)
            assertEquals("QR-VMess-Node", parsedVmess?.name)
            assertEquals(8443, parsedVmess?.port)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `retrofit and coroutines speed tester measures ping download upload and finds fastest server`() = runBlocking {
        // Real local HTTP ServerSocket supporting HEAD /ping, GET /down, and POST /up
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val executor = Executors.newCachedThreadPool()
        val payloadChunk = ByteArray(128 * 1024) { 0x5A }

        executor.submit {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    executor.submit {
                        try {
                            val input = BufferedInputStream(client.getInputStream())
                            val headers = readHeaders(input)
                            val requestLine = headers.firstOrNull() ?: ""
                            val contentLength = headers
                                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                            if (contentLength > 0) {
                                val discard = ByteArray(contentLength)
                                var remaining = contentLength
                                while (remaining > 0) {
                                    val r = input.read(discard, 0, remaining)
                                    if (r <= 0) break
                                    remaining -= r
                                }
                            }

                            val out = client.getOutputStream()
                            when {
                                requestLine.startsWith("HEAD ") -> {
                                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                                requestLine.startsWith("GET /down") -> {
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${payloadChunk.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.write(payloadChunk)
                                }
                                requestLine.startsWith("POST /up") -> {
                                    val okBody = "{\"ok\":true}".toByteArray()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${okBody.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.write(okBody)
                                }
                                else -> {
                                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                }
                            }
                            out.flush()
                            client.close()
                        } catch (_: Exception) {
                            try { client.close() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        try {
            val liveServer = ServerProfile(
                id = "fast-node",
                name = "Fast Loopback Node",
                protocol = ProtocolType.VLESS,
                address = "127.0.0.1",
                port = port
            )
            // Pick a guaranteed closed ephemeral port by opening and immediately closing a ServerSocket
            val closedPort = ServerSocket(0).use { it.localPort }
            val deadServer = ServerProfile(
                id = "dead-node",
                name = "Offline Node",
                protocol = ProtocolType.VMESS,
                address = "127.0.0.1",
                port = closedPort
            )

            val tester = NetworkSpeedTester(
                config = SpeedTestConfig(
                    baseUrl = "http://127.0.0.1:$port/",
                    downloadUrl = "http://127.0.0.1:$port/down",
                    uploadUrl = "http://127.0.0.1:$port/up",
                    httpLatencyUrl = "http://127.0.0.1:$port/ping",
                    uploadPayloadBytes = 32 * 1024,
                    timeoutMs = 1500,
                    pingSamples = 2
                )
            )

            val emissions = tester.benchmarkServers(listOf(deadServer, liveServer)).toList()
            val finalState = emissions.last()

            assertFalse(finalState.isRunning)
            assertEquals(2, finalState.completedCount)
            assertEquals("fast-node", finalState.fastestServerId)

            val fastResult = finalState.results.first { it.serverId == "fast-node" }
            assertEquals(SpeedTestStatus.COMPLETED, fastResult.status)
            assertTrue(fastResult.isReachable)
            assertTrue("Expected positive ping ms, got ${fastResult.pingMs}", fastResult.pingMs >= 1L)
            assertTrue("Expected positive download Mbps, got ${fastResult.downloadMbps}", fastResult.downloadMbps > 0.0)
            assertTrue("Expected positive upload Mbps, got ${fastResult.uploadMbps}", fastResult.uploadMbps > 0.0)

            val deadResult = finalState.results.first { it.serverId == "dead-node" }
            assertEquals(SpeedTestStatus.FAILED, deadResult.status)
            assertFalse(deadResult.isReachable)
            assertEquals(-1L, deadResult.pingMs)
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `strict vpn policy enforces self-exclusion, ipv6 lock, kill switch, split tunnel, and local proxy engine`() = runBlocking {
        val service = Robolectric.setupService(BpbVpnService::class.java)
        val builder = service.Builder()

        val routing = RoutingSettings(
            splitTunnelEnabled = true,
            splitTunnelMode = SplitTunnelMode.BYPASS_SELECTED_ONLY,
            selectedPackages = setOf("com.example.bypass.app", context.packageName)
        )
        val general = GeneralSettings(
            killSwitchEnabled = true,
            socksPort = 0,
            httpPort = 0
        )

        val disallowedCalls = mutableListOf<String>()
        val snapshot = BpbVpnService.applyStrictPolicy(
            builder = builder,
            packageName = context.packageName,
            serverName = "Strict Play Node",
            mtu = 1420,
            routing = routing,
            general = general,
            disallowApp = { pkg -> disallowedCalls.add(pkg) }
        )

        assertTrue(snapshot.selfExcluded)
        assertTrue(disallowedCalls.contains(context.packageName))
        assertTrue(disallowedCalls.contains("com.example.bypass.app"))
        assertEquals(2, disallowedCalls.size)
        assertTrue(snapshot.ipv4DefaultRoute)
        assertTrue(snapshot.ipv6LeakBlocked)
        assertTrue(snapshot.allowBypassDisabled)
        assertTrue(snapshot.killSwitchBlocking)
        assertTrue(snapshot.splitTunnelEnabled)
        assertTrue(snapshot.splitTunnelPackages.contains("com.example.bypass.app"))
        assertFalse("Own package should not be duplicated in split-tunnel package list", snapshot.splitTunnelPackages.contains(context.packageName))
        assertTrue(snapshot.dnsServers.contains("1.1.1.1"))

        // Verify real LocalProxyEngine HTTP CONNECT forwarding + socket protection + TLS fragmentation
        val echoServer = ServerSocket(0)
        val echoPort = echoServer.localPort
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            try {
                val upstreamClient = echoServer.accept()
                val inStream = upstreamClient.getInputStream()
                val outStream = upstreamClient.getOutputStream()
                val buf = ByteArray(128)
                var echoedTotal = 0
                while (echoedTotal < 13) {
                    val n = inStream.read(buf)
                    if (n <= 0) break
                    outStream.write(buf, 0, n)
                    outStream.flush()
                    echoedTotal += n
                }
                upstreamClient.close()
            } catch (_: Exception) {
            }
        }

        val protectorInvoked = AtomicBoolean(false)
        val proxyEngine = LocalProxyEngine(socketProtector = {
            protectorInvoked.set(true)
            true
        })
        try {
            proxyEngine.start(
                socksPort = 0,
                httpPort = 0,
                allowLan = false,
                fragmentConfig = FragmentConfig(enabled = true, length = "5-8", interval = "1-2")
            )
            val httpProxyPort = proxyEngine.activeHttpPort
            assertTrue(httpProxyPort > 0)

            Socket("127.0.0.1", httpProxyPort).use { client ->
                client.soTimeout = 3000
                val out = client.getOutputStream()
                out.write("CONNECT 127.0.0.1:$echoPort HTTP/1.1\r\nHost: 127.0.0.1:$echoPort\r\n\r\n".toByteArray())
                out.flush()

                val rawIn = client.getInputStream()
                val headerBuilder = StringBuilder()
                while (!headerBuilder.endsWith("\r\n\r\n")) {
                    val b = rawIn.read()
                    if (b == -1) break
                    headerBuilder.append(b.toChar())
                }
                val statusLine = headerBuilder.toString().lineSequence().firstOrNull()
                assertTrue("Expected 200 Connection Established, got $statusLine", statusLine?.contains("200") == true)

                // Send TLS ClientHello-prefixed payload (triggers real TCP fragmentation path)
                val tlsPayload = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x08, 0x01, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47)
                out.write(tlsPayload)
                out.flush()

                val echoed = ByteArray(tlsPayload.size)
                var totalRead = 0
                while (totalRead < tlsPayload.size) {
                    val r = rawIn.read(echoed, totalRead, tlsPayload.size - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                assertEquals(tlsPayload.size, totalRead)
                assertEquals(tlsPayload.toList(), echoed.toList())
            }

            assertTrue("Expected socketProtector to be called on upstream socket", protectorInvoked.get())
            assertTrue(proxyEngine.totalTxBytes.get() >= 13L)
            assertTrue(proxyEngine.totalRxBytes.get() >= 13L)
        } finally {
            proxyEngine.stop()
            echoServer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `ui displays speed test fastest selection, qr scanner photo picker, and vpn consent disclosure`() {
        var selectedFastest = false
        var acceptedConsent = false

        val fastestNode = ServerSpeedResult(
            serverId = "fastest-1",
            serverName = "Tokyo Edge Fast",
            protocolLabel = "VLESS",
            address = "tokyo.example.net",
            port = 443,
            pingMs = 24L,
            jitterMs = 3L,
            downloadMbps = 185.4,
            uploadMbps = 62.1,
            isReachable = true,
            status = SpeedTestStatus.COMPLETED
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                var showConsent by remember { mutableStateOf(true) }
                var showSpeedTest by remember { mutableStateOf(false) }
                var showQr by remember { mutableStateOf(false) }

                ServersScreen(
                    servers = emptyList(),
                    selectedServerId = null,
                    onSelectServer = {},
                    onToggleFavorite = {},
                    onDeleteServer = {},
                    onPingServer = {},
                    onPingAll = {},
                    onOpenAddDialog = {},
                    onOpenQrScanner = { showQr = true },
                    onOpenSpeedTest = { showSpeedTest = true }
                )

                if (showConsent) {
                    VpnConsentDialog(
                        onAccept = {
                            acceptedConsent = true
                            showConsent = false
                            showSpeedTest = true
                        },
                        onDecline = { showConsent = false }
                    )
                }

                if (showSpeedTest) {
                    SpeedTestDialog(
                        session = SpeedTestSessionState(
                            isRunning = false,
                            completedCount = 1,
                            totalCount = 1,
                            results = listOf(fastestNode),
                            fastestServerId = "fastest-1"
                        ),
                        onStartTest = {},
                        onStopTest = {},
                        onSelectFastest = {
                            selectedFastest = true
                            showSpeedTest = false
                            showQr = true
                        },
                        onSelectServerId = {},
                        onDismiss = { showSpeedTest = false }
                    )
                }

                if (showQr) {
                    QrScannerDialog(
                        onDismiss = { showQr = false },
                        onQrConfigDecoded = { true }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Accept prominent VpnService disclosure dialog
        composeTestRule.onNodeWithText("How BPB VPN Uses VpnService").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accept_vpn_disclosure_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(acceptedConsent)

        // 2. Verify SpeedTestDialog ranks fastest server & triggers Select Fastest
        assertTrue(composeTestRule.onAllNodesWithText("Tokyo Edge Fast").fetchSemanticsNodes().isNotEmpty())
        composeTestRule.onNodeWithTag("select_fastest_server_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectedFastest)

        // 3. Verify QrScannerDialog exposes camera permission & zero-permission Photo Picker button
        composeTestRule.onNodeWithTag("pick_qr_image_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_qr_dialog_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Verify ServersScreen header actions are visible
        composeTestRule.onNodeWithTag("scan_qr_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_test_button").assertIsDisplayed()
    }

    private fun readHeaders(input: BufferedInputStream): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var prevCr = false
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (b == '\n'.code) {
                val line = sb.toString()
                sb.clear()
                if (line.isEmpty()) break
                lines.add(line)
                prevCr = false
            } else if (b == '\r'.code) {
                prevCr = true
            } else {
                if (prevCr) sb.append('\r')
                prevCr = false
                sb.append(b.toChar())
            }
        }
        return lines
    }
}
