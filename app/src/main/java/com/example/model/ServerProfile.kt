package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "servers")
data class ServerProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProtocolType,
    val address: String,
    val port: Int = 443,
    val uuid: String = "",
    val alterId: Int = 0,
    val cipher: String = "auto",
    val flow: String = "",
    val network: String = "ws", // tcp, ws, grpc, httpupgrade
    val security: String = "tls", // tls, reality, none
    val serverName: String = "", // SNI
    val path: String = "/",
    val publicKey: String = "", // Reality pbk
    val shortId: String = "", // Reality sid
    val fingerprint: String = "chrome",
    val pingMs: Long = -1L,
    val countryCode: String = "GLOBAL",
    val isFavorite: Boolean = false,
    val customCleanIp: String = "",
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ProtocolType {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    WARP,
    CLOUDFLARE_WORKER,
    PSIPHON
}
