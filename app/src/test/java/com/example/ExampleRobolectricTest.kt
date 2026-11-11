package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DefaultServers
import com.example.model.AntiCensorshipSettings
import com.example.model.GeneralSettings
import com.example.model.ProtocolType
import com.example.model.RoutingSettings
import com.example.service.ConfigGenerator
import com.example.service.UriParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BPB VPN", appName)
    }

    @Test
    fun `test vless uri parsing`() {
        val uri = "vless://a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d@de.node.com:443?type=ws&security=tls&path=%2Ftunnel&sni=de.node.com#Frankfurt-VLESS"
        val profile = UriParser.parse(uri)
        assertNotNull(profile)
        assertEquals("Frankfurt-VLESS", profile?.name)
        assertEquals(ProtocolType.VLESS, profile?.protocol)
        assertEquals("de.node.com", profile?.address)
        assertEquals(443, profile?.port)
        assertEquals("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", profile?.uuid)
        assertEquals("ws", profile?.network)
        assertEquals("tls", profile?.security)
    }

    @Test
    fun `test trojan uri parsing`() {
        val uri = "trojan://secretpassword@nl.proxy.net:443?type=tcp&security=tls&sni=nl.proxy.net#Amsterdam-Trojan"
        val profile = UriParser.parse(uri)
        assertNotNull(profile)
        assertEquals("Amsterdam-Trojan", profile?.name)
        assertEquals(ProtocolType.TROJAN, profile?.protocol)
        assertEquals("secretpassword", profile?.uuid)
        assertEquals(443, profile?.port)
    }

    @Test
    fun `test worker url parsing`() {
        val url = "https://bpb-freedom.workers.dev/bpb-tunnel"
        val profile = UriParser.parse(url)
        assertNotNull(profile)
        assertEquals(ProtocolType.CLOUDFLARE_WORKER, profile?.protocol)
        assertEquals("bpb-freedom.workers.dev", profile?.address)
        assertEquals("/bpb-tunnel", profile?.path)
    }

    @Test
    fun `test config generator generates valid json`() {
        val server = DefaultServers.getInitialServers().first()
        val antiCensorship = AntiCensorshipSettings()
        val routing = RoutingSettings()
        val general = GeneralSettings()

        val jsonString = ConfigGenerator.generateXrayConfig(server, antiCensorship, routing, general)
        val json = JSONObject(jsonString)

        assertTrue(json.has("inbounds"))
        assertTrue(json.has("outbounds"))
        assertTrue(json.has("routing"))
        assertTrue(json.has("dns"))

        val inbounds = json.getJSONArray("inbounds")
        assertEquals(2, inbounds.length())
    }
}
