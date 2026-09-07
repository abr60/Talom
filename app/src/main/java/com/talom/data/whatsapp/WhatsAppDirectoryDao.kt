package com.talom.data.whatsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhatsAppDirectoryDao {
    @Query("SELECT * FROM whatsapp_directory ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<WhatsAppDirectoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WhatsAppDirectoryEntity>)

    @Query("SELECT COUNT(*) FROM whatsapp_directory")
    suspend fun count(): Int
}
