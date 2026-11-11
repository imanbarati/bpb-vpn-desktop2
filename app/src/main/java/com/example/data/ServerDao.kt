package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.ServerProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllServers(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): ServerProfile?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerProfile>)

    @Update
    suspend fun updateServer(server: ServerProfile)

    @Delete
    suspend fun deleteServer(server: ServerProfile)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE servers SET pingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: String, pingMs: Long)

    @Query("UPDATE servers SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM servers")
    suspend fun clearAll()
}
