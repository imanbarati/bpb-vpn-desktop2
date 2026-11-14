package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.AntiCensorshipSettings
import com.example.model.GeneralSettings
import com.example.model.RoutingSettings
import com.example.model.SplitTunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

data class StrictVpnPolicySnapshot(
    val selfExcluded: Boolean,
    val ipv4DefaultRoute: Boolean,
    val ipv6LeakBlocked: Boolean,
    val allowBypassDisabled: Boolean,
    val killSwitchBlocking: Boolean,
    val splitTunnelEnabled: Boolean,
    val splitTunnelMode: SplitTunnelMode,
    val splitTunnelPackages: Set<String>,
    val dnsServers: List<String>,
    val mtu: Int,
    val httpProxyPort: Int?,
    val socksProxyPort: Int
)

/**
 * Play-Store compliant, strict Android VpnService implementation:
 * - Always excludes itself via `addDisallowedApplication(packageName)` to eliminate routing loops.
 * - Blocks IPv6 leaks by capturing `::/0` alongside IPv4 `0.0.0.0/0`.
 * - Enforces per-app split tunneling (`addAllowedApplication` / `addDisallowedApplication`).
 * - Never calls `allowBypass()`, enables `setBlocking(true)` when Kill Switch is active, and pins DNS.
 * - Runs a local protected SOCKS5/HTTP forwarder ([LocalProxyEngine]) with optional TLS fragmentation.
 */
class BpbVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunDrainJob: Job? = null
    private var proxyEngine: LocalProxyEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "BPB Secure Node"
            val mtu = intent.getIntExtra(EXTRA_MTU, 1420)
            startTunnel(serverName, mtu)
        }

        return START_STICKY
    }

    private fun startTunnel(serverName: String, mtu: Int) {
        try {
            val notification = buildNotification(serverName)
            startForeground(NOTIFICATION_ID, notification)

            val appPrefs = (application as? com.example.BpbApplication)?.preferencesRepository
            val routing = pendingRoutingSettings ?: appPrefs?.routingSettings?.value ?: RoutingSettings()
            val general = pendingGeneralSettings ?: appPrefs?.generalSettings?.value ?: GeneralSettings()
            val antiCensorship = pendingAntiCensorshipSettings ?: appPrefs?.antiCensorshipSettings?.value ?: AntiCensorshipSettings()

            val builder = Builder()
            val policy = applyStrictPolicy(
                builder = builder,
                packageName = packageName,
                serverName = serverName,
                mtu = mtu,
                routing = routing,
                general = general
            )
            lastAppliedPolicy = policy

            vpnInterface = builder.establish()
            VpnController.log(
                "VPN_SERVICE",
                "Strict TUN established (MTU=$mtu, IPv6-lock=${policy.ipv6LeakBlocked}, KillSwitch=${policy.killSwitchBlocking}, SelfExcluded=${policy.selfExcluded})"
            )

            // Start protected local SOCKS5 & HTTP proxy forwarder with optional TLS fragmentation
            val engine = LocalProxyEngine(socketProtector = { sock -> protect(sock) })
            engine.start(
                socksPort = general.socksPort,
                httpPort = general.httpPort,
                allowLan = general.allowLanSharing,
                fragmentConfig = antiCensorship.fragment
            )
            proxyEngine = engine
            activeProxyEngine = engine
            VpnController.log(
                "PROXY_ENGINE",
                "Local protected forwarder listening on SOCKS5:${engine.activeSocksPort}, HTTP:${engine.activeHttpPort}"
            )

            // Start non-blocking TUN packet reader loop
            vpnInterface?.let { pfd ->
                tunDrainJob?.cancel()
                tunDrainJob = serviceScope.launch {
                    drainTunInterface(pfd)
                }
            }

            // Start background battery monitoring with user-configured power threshold
            BatteryDrainMonitor.startMonitoring(
                this,
                thresholdPercent = general.batteryDrainThresholdPercent
            )
        } catch (e: Exception) {
            VpnController.log("VPN_SERVICE", "Error establishing strict TUN: ${e.localizedMessage}", com.example.model.LogLevel.ERROR)
        }
    }

    private fun drainTunInterface(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        try {
            while (serviceScope.isActive && vpnInterface != null) {
                packet.clear()
                val length = input.read(packet.array())
                if (length > 0) {
                    tunRxBytes.addAndGet(length.toLong())
                } else if (length < 0) {
                    break
                } else {
                    Thread.sleep(20)
                }
            }
        } catch (_: Exception) {
            // Normal on interface close
        }
    }

    private fun stopTunnel() {
        try {
            BatteryDrainMonitor.stopMonitoring()
            tunDrainJob?.cancel()
            tunDrainJob = null
            proxyEngine?.stop()
            proxyEngine = null
            if (activeProxyEngine != null) {
                activeProxyEngine = null
            }
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            VpnController.log("VPN_SERVICE", "TUN interface and local proxy closed cleanly")
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        VpnController.stopVpn(applicationContext)
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BPB VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status and bandwidth"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, BpbVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BPB VPN Connected")
            .setContentText("Strict encrypted tunnel active via $serverName")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.service.START_VPN"
        const val ACTION_STOP = "com.example.service.STOP_VPN"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_MTU = "extra_mtu"

        private const val CHANNEL_ID = "bpb_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var pendingRoutingSettings: RoutingSettings? = null

        @Volatile
        var pendingGeneralSettings: GeneralSettings? = null

        @Volatile
        var pendingAntiCensorshipSettings: AntiCensorshipSettings? = null

        @Volatile
        var lastAppliedPolicy: StrictVpnPolicySnapshot? = null

        @Volatile
        var activeProxyEngine: LocalProxyEngine? = null

        val tunRxBytes = AtomicLong(0L)

        /**
         * Configures Android [VpnService.Builder] under strict VPN & Play Store security rules.
         * Extracted cleanly so both the live service and Robolectric tests can verify strict policy behavior.
         */
        fun applyStrictPolicy(
            builder: Builder,
            packageName: String,
            serverName: String,
            mtu: Int,
            routing: RoutingSettings,
            general: GeneralSettings,
            allowApp: (String) -> Unit = { pkg -> builder.addAllowedApplication(pkg) },
            disallowApp: (String) -> Unit = { pkg -> builder.addDisallowedApplication(pkg) }
        ): StrictVpnPolicySnapshot {
            builder.setSession("BPB VPN - $serverName")
                .setMtu(mtu.coerceIn(1200, 1500))
                .addAddress(java.net.InetAddress.getByName("10.0.0.2"), 24)
                .addRoute(java.net.InetAddress.getByName("0.0.0.0"), 0)
                // Capture IPv6 default route to prevent IPv6 traffic leaking outside tunnel
                .addAddress(java.net.InetAddress.getByName("fd00:b0b::2"), 128)
                .addRoute(java.net.InetAddress.getByName("::"), 0)

            val dnsList = listOfNotNull(
                general.dns.primaryDns.takeIf { it.isNotBlank() },
                general.dns.secondaryDns.takeIf { it.isNotBlank() }
            ).distinct().ifEmpty { listOf("1.1.1.1", "1.0.0.1") }

            dnsList.forEach { dnsIp ->
                try {
                    builder.addDnsServer(java.net.InetAddress.getByName(dnsIp))
                } catch (_: Exception) {
                    // Ignore malformed user DNS entry
                }
            }

            // Kill switch / strict lockdown: never invoke allowBypass(); enforce blocking I/O
            if (general.killSwitchEnabled) {
                builder.setBlocking(true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                try {
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", general.httpPort))
                } catch (_: Exception) {
                    // Ignore if platform rejects proxy info
                }
            }

            var selfExcluded = false
            val appliedSplitPackages = mutableSetOf<String>()

            if (routing.splitTunnelEnabled && routing.selectedPackages.isNotEmpty()) {
                when (routing.splitTunnelMode) {
                    SplitTunnelMode.PROXY_SELECTED_ONLY -> {
                        routing.selectedPackages
                            .filter { it != packageName }
                            .forEach { pkg ->
                                try {
                                    allowApp(pkg)
                                    appliedSplitPackages.add(pkg)
                                } catch (_: Exception) {
                                }
                            }
                        // Excluded by omission when allowed-list is active
                        selfExcluded = !appliedSplitPackages.contains(packageName)
                    }
                    SplitTunnelMode.BYPASS_SELECTED_ONLY -> {
                        try {
                            disallowApp(packageName)
                            selfExcluded = true
                        } catch (_: Exception) {
                        }
                        routing.selectedPackages
                            .filter { it != packageName }
                            .forEach { pkg ->
                                try {
                                    disallowApp(pkg)
                                    appliedSplitPackages.add(pkg)
                                } catch (_: Exception) {
                                }
                            }
                    }
                }
            } else {
                // Strict self-exclusion so VPN's own upstream sockets never loop into tun0
                try {
                    disallowApp(packageName)
                    selfExcluded = true
                } catch (_: Exception) {
                    selfExcluded = false
                }
            }

            return StrictVpnPolicySnapshot(
                selfExcluded = selfExcluded,
                ipv4DefaultRoute = true,
                ipv6LeakBlocked = true,
                allowBypassDisabled = true,
                killSwitchBlocking = general.killSwitchEnabled,
                splitTunnelEnabled = routing.splitTunnelEnabled,
                splitTunnelMode = routing.splitTunnelMode,
                splitTunnelPackages = appliedSplitPackages,
                dnsServers = dnsList,
                mtu = mtu.coerceIn(1200, 1500),
                httpProxyPort = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) general.httpPort else null,
                socksProxyPort = general.socksPort
            )
        }
    }
}
