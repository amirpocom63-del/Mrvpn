package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.VpnServer
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<VpnServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: VpnServer)

    @Update
    suspend fun updateServer(server: VpnServer)

    @Delete
    suspend fun deleteServer(server: VpnServer)

    @Query("SELECT * FROM vpn_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): VpnServer?

    @Query("UPDATE vpn_servers SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE vpn_servers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultServer(id: String)

    @Query("DELETE FROM vpn_servers WHERE isUserConfig = 0")
    suspend fun clearAllServers()

    @Query("SELECT * FROM vpn_servers ORDER BY name ASC")
    suspend fun getAllServersSync(): List<VpnServer>
}
