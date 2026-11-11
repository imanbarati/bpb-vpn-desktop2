package com.example

import com.example.data.DefaultServers
import com.example.model.ProtocolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDefaultServersLoaded() {
        val servers = DefaultServers.getInitialServers()
        assertTrue(servers.isNotEmpty())
        assertTrue(servers.any { it.protocol == ProtocolType.CLOUDFLARE_WORKER })
        assertTrue(servers.any { it.protocol == ProtocolType.VLESS })
        assertTrue(servers.any { it.protocol == ProtocolType.WARP })
    }
}
