package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AntiCensorshipSettings
import com.example.model.GeneralSettings
import com.example.model.ProtocolType
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import com.example.model.VpnStatus
import com.example.service.AdaptiveReverseTunnelPolicy
import com.example.service.ConfigGenerator
import com.example.service.FirefoxVpnAccount
import com.example.service.FirefoxVpnAccountPool
import com.example.service.FreeVpnBackendAdapter
import com.example.service.TunnelPolicyArm
import com.example.service.UriParser
import com.example.service.VpnController
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackendFunctionalTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        VpnController.resetForTesting()
        FreeVpnBackendAdapter.firefoxAccountPool.resetAccounts(
            FirefoxVpnAccountPool.defaultFreeTierAccounts()
        )
        FreeVpnBackendAdapter.reverseTunnelPolicy.reset()
    }

    @After
    fun tearDown() {
        VpnController.resetForTesting()
    }

    @Test
    fun `uri parser supports firefox vpn, reverse tunnel, and query-bearing shadowsocks`() {
        val fxProfile = UriParser.parse(
            "fxvpn://fxa-token-99@fastly.mozilla-edge.net:443?type=h2&sni=fastly.mozilla-edge.net&path=%2Ffx-h3#Firefox-Free-US"
        )
        assertNotNull(fxProfile)
        assertEquals(ProtocolType.FIREFOX_VPN, fxProfile?.protocol)
        assertEquals("Firefox-Free-US", fxProfile?.name)
        assertEquals("fastly.mozilla-edge.net", fxProfile?.address)
        assertEquals(443, fxProfile?.port)
        assertEquals("fxa-token-99", fxProfile?.uuid)
        assertEquals("h2", fxProfile?.network)
        assertEquals("/fx-h3", fxProfile?.path)

        val revProfile = UriParser.parse(
            "revtun://secret-robot-key@relay.robot-edge.org:8443?mode=ws&path=%2Frev-ws#Robot-Reverse-DE"
        )
        assertNotNull(revProfile)
        assertEquals(ProtocolType.REVERSE_TUNNEL, revProfile?.protocol)
        assertEquals("Robot-Reverse-DE", revProfile?.name)
        assertEquals("relay.robot-edge.org", revProfile?.address)
        assertEquals(8443, revProfile?.port)
        assertEquals("secret-robot-key", revProfile?.uuid)
        assertEquals("ws", revProfile?.network)
        assertEquals("/rev-ws", revProfile?.path)

        val ssProfile = UriParser.parse(
            "ss://YWVzLTEyOC1nY206cGFzc3dvcmQ=@ss.host.net:9001/?plugin=v2ray-plugin#SS-Query"
        )
        assertNotNull(ssProfile)
        assertEquals(ProtocolType.SHADOWSOCKS, ssProfile?.protocol)
        assertEquals("ss.host.net", ssProfile?.address)
        assertEquals(9001, ssProfile?.port)

        assertNull(UriParser.parse("invalid://not-supported"))
    }

    @Test
    fun `config generator builds valid xray config with fragment-dialer and new protocols`() {
        val fxServer = ServerProfile(
            name = "Firefox Fastly H2/H3",
            protocol = ProtocolType.FIREFOX_VPN,
            address = "proxy.fastly-edge.mozilla-relay.net",
            port = 443,
            uuid = "fxa-token-123",
            network = "h2",
            security = "tls",
            path = "/fxvpn-h3"
        )
        val antiCensorship = AntiCensorshipSettings()
        val fxJson = JSONObject(
            ConfigGenerator.generateXrayConfig(fxServer, antiCensorship, RoutingSettings(), GeneralSettings())
        )

        val outbounds = fxJson.getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertTrue("Expected fragment-dialer outbound when fragment is enabled", tags.contains("fragment-dialer"))

        val proxyOutbound = outbounds.getJSONObject(0)
        assertEquals("http", proxyOutbound.getString("protocol"))
        val streamSettings = proxyOutbound.getJSONObject("streamSettings")
        assertEquals("h2", streamSettings.getString("network"))
        val alpn = streamSettings.getJSONObject("tlsSettings").getJSONArray("alpn")
        assertEquals("h3", alpn.getString(0))
        assertEquals("h2", alpn.getString(1))
        assertEquals("fragment-dialer", streamSettings.getJSONObject("sockopt").getString("dialerProxy"))

        val revServer = ServerProfile(
            name = "Robot Reverse",
            protocol = ProtocolType.REVERSE_TUNNEL,
            address = "relay.redesigned-tunnel.net",
            port = 443,
            uuid = "rev-secret",
            network = "ws",
            security = "tls"
        )
        val revJson = JSONObject(
            ConfigGenerator.generateXrayConfig(revServer, antiCensorship, RoutingSettings(), GeneralSettings())
        )
        assertTrue(revJson.has("reverse"))
        val bridges = revJson.getJSONObject("reverse").getJSONArray("bridges")
        assertEquals("reverse-bridge", bridges.getJSONObject(0).getString("tag"))
    }

    @Test
    fun `firefox vpn multi-account pool rotates on quota exhaustion and rate limit`() {
        val pool = FirefoxVpnAccountPool(
            listOf(
                FirefoxVpnAccount(id = "acc-1", label = "Slot 1", bearerToken = "tok-1", monthlyQuotaBytes = 1000L),
                FirefoxVpnAccount(id = "acc-2", label = "Slot 2", bearerToken = "tok-2", monthlyQuotaBytes = 1000L)
            )
        )

        val first = pool.acquireActiveAccount()
        assertEquals("acc-1", first?.id)

        // Exhaust acc-1 quota -> should rotate cleanly to acc-2
        pool.recordUsage("acc-1", 1000L)
        val second = pool.acquireActiveAccount()
        assertEquals("acc-2", second?.id)

        // Rate-limit acc-2 -> pool exhausted, returns null
        val third = pool.markRateLimitedAndRotate("acc-2")
        assertNull(third)
    }

    @Test
    fun `adaptive reverse tunnel policy learns best arm from latency and failure feedback`() {
        val policy = AdaptiveReverseTunnelPolicy()

        // First round explores all 4 arms
        val explored = mutableSetOf<TunnelPolicyArm>()
        repeat(4) {
            val arm = policy.selectOptimalArm()
            explored.add(arm)
            when (arm) {
                TunnelPolicyArm.NOISE_QUIC_H3 -> policy.recordOutcome(arm, success = true, rttMs = 35L, throughputKbps = 4096.0)
                TunnelPolicyArm.FRAGMENT_SMALL_H2 -> policy.recordOutcome(arm, success = false, rttMs = -1L)
                else -> policy.recordOutcome(arm, success = true, rttMs = 450L, throughputKbps = 512.0)
            }
        }
        assertEquals(4, explored.size)

        // Next pull should prefer the highest-reward arm (NOISE_QUIC_H3)
        val bestArm = policy.selectOptimalArm()
        assertEquals(TunnelPolicyArm.NOISE_QUIC_H3, bestArm)

        val tuned = policy.applyArmToSettings(bestArm, AntiCensorshipSettings())
        assertTrue(tuned.fragment.enabled)
        assertEquals(bestArm.fragmentLength, tuned.fragment.length)
        assertEquals(bestArm.noisePackets, tuned.noise.packetCount)
    }

    @Test
    fun `functional socket handshake and vpn controller lifecycle for firefox and reverse tunnel backends`() = runBlocking {
        // Spin up a real local loopback TCP server acting as free VPN HTTP/WS backend
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val executor = Executors.newSingleThreadExecutor()
        val receivedRequests = mutableListOf<String>()

        executor.submit {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val firstLine = reader.readLine() ?: ""
                    synchronized(receivedRequests) {
                        receivedRequests.add(firstLine)
                    }
                    client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    client.getOutputStream().flush()
                    client.close()
                } catch (_: Exception) {
                    break
                }
            }
        }

        try {
            VpnController.connectDelayMs = 50L
            VpnController.disconnectDelayMs = 50L
            VpnController.requireBackendProbe = true

            // 1. Test FIREFOX_VPN against live local socket
            val fxServer = ServerProfile(
                id = "fx-loopback",
                name = "Firefox Loopback",
                protocol = ProtocolType.FIREFOX_VPN,
                address = "127.0.0.1",
                port = port,
                network = "h2"
            )
            VpnController.startVpn(context, fxServer, AntiCensorshipSettings(), RoutingSettings(), GeneralSettings())

            waitUntil(3000) { VpnController.connectionState.value.status == VpnStatus.CONNECTED }
            assertEquals(VpnStatus.CONNECTED, VpnController.connectionState.value.status)
            assertTrue(VpnController.logs.value.any { it.tag == "FIREFOX_VPN" })
            assertTrue(VpnController.logs.value.any { it.tag == "PROBE" && it.message.contains("200 Connection Established") })

            VpnController.stopVpn(context)
            waitUntil(2000) { VpnController.connectionState.value.status == VpnStatus.DISCONNECTED }

            // 2. Test REVERSE_TUNNEL against live local socket
            val revServer = ServerProfile(
                id = "rev-loopback",
                name = "Robot Reverse Loopback",
                protocol = ProtocolType.REVERSE_TUNNEL,
                address = "127.0.0.1",
                port = port,
                network = "ws"
            )
            VpnController.startVpn(context, revServer, AntiCensorshipSettings(), RoutingSettings(), GeneralSettings())

            waitUntil(3000) { VpnController.connectionState.value.status == VpnStatus.CONNECTED }
            assertEquals(VpnStatus.CONNECTED, VpnController.connectionState.value.status)
            assertTrue(VpnController.logs.value.any { it.tag == "REVERSE_POLICY" })
            assertTrue(receivedRequests.any { it.startsWith("CONNECT ") })
            assertTrue(receivedRequests.any { it.startsWith("GET /reverse-bridge") })

            VpnController.stopVpn(context)
            waitUntil(2000) { VpnController.connectionState.value.status == VpnStatus.DISCONNECTED }

            // 3. Test failure handling when backend port is unreachable
            serverSocket.close()
            VpnController.startVpn(context, fxServer, AntiCensorshipSettings(), RoutingSettings(), GeneralSettings())
            waitUntil(3000) { VpnController.connectionState.value.status == VpnStatus.ERROR }
            assertEquals(VpnStatus.ERROR, VpnController.connectionState.value.status)
            assertFalse(VpnController.connectionState.value.errorMessage.isNullOrBlank())
        } finally {
            if (!serverSocket.isClosed) serverSocket.close()
            executor.shutdownNow()
        }
    }

    private suspend fun waitUntil(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            kotlinx.coroutines.delay(25)
        }
    }
}
