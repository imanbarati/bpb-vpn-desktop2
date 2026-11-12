package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BatteryDrainStats(
    val currentBatteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isTunnelRunning: Boolean = false,
    val isIdling: Boolean = false,
    val idleDurationSeconds: Long = 0,
    val startIdleBatteryLevel: Int = 100,
    val batteryDroppedDuringIdle: Int = 0,
    val estimatedDrainRatePerHour: Float = 0f,
    val hasAlerted: Boolean = false,
    val lastAlertMessage: String? = null
)

/**
 * Background Service Monitoring feature that notifies the user if the VPN
 * is consuming excessive battery while idling in the background.
 */
object BatteryDrainMonitor {

    private const val CHANNEL_ID = "bpb_battery_drain_channel"
    private const val NOTIFICATION_ID = 2002
    private const val ALERT_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes between alerts

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val _stats = MutableStateFlow(BatteryDrainStats())
    val stats: StateFlow<BatteryDrainStats> = _stats.asStateFlow()

    private var lastAlertTimeMs: Long = 0
    private var idleStartTimeMs: Long = 0
    private var baselineBatteryLevel: Int = -1

    fun startMonitoring(context: Context, thresholdPercent: Int = 3) {
        createNotificationChannel(context)
        if (monitorJob?.isActive == true) return

        baselineBatteryLevel = getBatteryLevel(context)
        idleStartTimeMs = System.currentTimeMillis()

        monitorJob = monitorScope.launch {
            VpnController.log("BATTERY_GUARD", "Battery drain background monitor initialized (threshold: $thresholdPercent%)")

            while (isActive) {
                delay(10_000) // Poll every 10 seconds

                val isRunning = VpnController.connectionState.value.status == VpnStatus.CONNECTED
                if (!isRunning) {
                    _stats.value = _stats.value.copy(
                        isTunnelRunning = false,
                        isIdling = false,
                        idleDurationSeconds = 0
                    )
                    continue
                }

                val currentLevel = getBatteryLevel(context)
                val isCharging = isDeviceCharging(context)
                val (rxSpeed, txSpeed) = getCurrentSpeeds()

                // Tunnel is considered idling when payload throughput is minimal (< 10 KB/s both ways)
                val isTrafficIdle = rxSpeed < 10_000 && txSpeed < 10_000

                if (isTrafficIdle) {
                    val idleSec = _stats.value.idleDurationSeconds + 10
                    if (baselineBatteryLevel < 0 || _stats.value.idleDurationSeconds == 0L) {
                        baselineBatteryLevel = currentLevel
                        idleStartTimeMs = System.currentTimeMillis()
                    }

                    val drop = if (!isCharging && baselineBatteryLevel > currentLevel) {
                        baselineBatteryLevel - currentLevel
                    } else 0

                    val idleHours = (idleSec / 3600f).coerceAtLeast(0.01f)
                    val drainPerHour = drop / idleHours

                    _stats.value = BatteryDrainStats(
                        currentBatteryLevel = currentLevel,
                        isCharging = isCharging,
                        isTunnelRunning = true,
                        isIdling = true,
                        idleDurationSeconds = idleSec,
                        startIdleBatteryLevel = baselineBatteryLevel,
                        batteryDroppedDuringIdle = drop,
                        estimatedDrainRatePerHour = drainPerHour,
                        hasAlerted = _stats.value.hasAlerted,
                        lastAlertMessage = _stats.value.lastAlertMessage
                    )

                    // Check if battery drop or drain rate exceeded safety threshold while idling
                    val exceededDropThreshold = drop >= thresholdPercent
                    val exceededRateThreshold = idleSec >= 60 && drainPerHour >= 5.0f && drop >= 1
                    val isCooldownPassed = (System.currentTimeMillis() - lastAlertTimeMs) > ALERT_COOLDOWN_MS

                    if (!isCharging && (exceededDropThreshold || exceededRateThreshold) && isCooldownPassed) {
                        notifyExcessiveDrain(
                            context = context,
                            batteryDrop = drop,
                            idleMinutes = (idleSec / 60).toInt()
                        )
                        lastAlertTimeMs = System.currentTimeMillis()
                        _stats.value = _stats.value.copy(
                            hasAlerted = true,
                            lastAlertMessage = "Excessive battery drain detected: $drop% drop while idling for ${idleSec / 60}m"
                        )
                    }
                } else {
                    // Active traffic is flowing, reset idle baseline
                    baselineBatteryLevel = currentLevel
                    _stats.value = _stats.value.copy(
                        currentBatteryLevel = currentLevel,
                        isCharging = isCharging,
                        isTunnelRunning = true,
                        isIdling = false,
                        idleDurationSeconds = 0,
                        startIdleBatteryLevel = currentLevel,
                        batteryDroppedDuringIdle = 0
                    )
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _stats.value = _stats.value.copy(
            isTunnelRunning = false,
            isIdling = false,
            idleDurationSeconds = 0
        )
    }

    /**
     * Manually triggers a sample battery drain notification so users can verify alert delivery.
     */
    fun triggerTestAlert(context: Context) {
        createNotificationChannel(context)
        notifyExcessiveDrain(
            context = context,
            batteryDrop = 4,
            idleMinutes = 15
        )
        _stats.value = _stats.value.copy(
            hasAlerted = true,
            lastAlertMessage = "Test alert triggered: 4% simulated drop while idling in background"
        )
        VpnController.log("BATTERY_GUARD", "Excessive battery drain test notification fired")
    }

    private fun notifyExcessiveDrain(context: Context, batteryDrop: Int, idleMinutes: Int) {
        try {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "SETTINGS")
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                101,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val disconnectIntent = Intent(context, BpbVpnService::class.java).apply {
                action = BpbVpnService.ACTION_STOP
            }
            val disconnectPendingIntent = PendingIntent.getService(
                context,
                102,
                disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val dropText = if (batteryDrop > 0) "$batteryDrop% battery drop" else "abnormal power usage"
            val idleText = if (idleMinutes > 0) "during $idleMinutes min of idle background connection" else "while idling in background"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("⚠️ VPN Battery Drain Warning")
                .setContentText("BPB VPN has consumed $dropText $idleText.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "BPB VPN is connected and idling with no active traffic, but has consumed $dropText $idleText. " +
                        "To prevent unwanted battery drain, you can disconnect or adjust anti-censorship fragment keep-alives."
                    )
                )
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Disconnect",
                    disconnectPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_preferences,
                    "Optimize",
                    openPendingIntent
                )
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            VpnController.log("BATTERY_GUARD", "Posted notification: excessive battery consumption ($dropText)")
        } catch (e: Exception) {
            VpnController.log("BATTERY_GUARD", "Failed to dispatch notification: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Battery & Background Health",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns if the VPN tunnel consumes excessive battery while idling in the background"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else 85 // Safe fallback if battery status cannot be read
        } catch (e: Exception) {
            85
        }
    }

    fun isDeviceCharging(context: Context): Boolean {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentSpeeds(): Pair<Long, Long> {
        val stats = VpnController.bandwidthStats.value
        return Pair(stats.downloadSpeedBps, stats.uploadSpeedBps)
    }
}
