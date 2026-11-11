package com.example.model

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class ConnectionState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeProfileId: String? = null,
    val activeProfile: ServerProfile? = null,
    val errorMessage: String? = null,
    val connectedAt: Long? = null
)

data class BandwidthStats(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalReceivedBytes: Long = 0L,
    val totalSentBytes: Long = 0L,
    val pingMs: Long = 0L,
    val sessionDurationSeconds: Long = 0L
)
