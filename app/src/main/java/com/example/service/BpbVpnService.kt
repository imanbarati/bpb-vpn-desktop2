package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class BpbVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

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

            val builder = Builder()
                .setSession("BPB VPN - $serverName")
                .setMtu(mtu)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            vpnInterface = builder.establish()
            VpnController.log("VPN_SERVICE", "TUN interface established with MTU $mtu")

            // Start background battery monitoring with user-configured power threshold
            val threshold = (application as? com.example.BpbApplication)
                ?.preferencesRepository?.generalSettings?.value?.batteryDrainThresholdPercent ?: 3
            BatteryDrainMonitor.startMonitoring(this, thresholdPercent = threshold)
        } catch (e: Exception) {
            VpnController.log("VPN_SERVICE", "Error establishing TUN: ${e.localizedMessage}")
        }
    }

    private fun stopTunnel() {
        try {
            BatteryDrainMonitor.stopMonitoring()
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            VpnController.log("VPN_SERVICE", "TUN interface closed")
        } catch (e: Exception) {
            // Ignore
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
            .setContentText("Routed securely via $serverName")
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
    }
}
