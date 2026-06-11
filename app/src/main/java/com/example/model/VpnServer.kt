package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vpn_servers")
data class VpnServer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val configUrl: String,
    val remarks: String = "",
    val isDefault: Boolean = false,
    val encryptedConfig: String = "",
    val encryptionKey: String = "",
    val isUserConfig: Boolean = false
)
