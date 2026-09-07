package com.talom.data.whatsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhatsAppWhitelistDao {
    @Query("SELECT * FROM whatsapp_whitelist ORDER BY addedAtMillis ASC")
    fun observeAll(): Flow<List<WhatsAppWhitelist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WhatsAppWhitelist)

    @Query("DELETE FROM whatsapp_whitelist WHERE jid = :jid")
    suspend fun delete(jid: String)

    @Query("SELECT jid FROM whatsapp_whitelist")
    suspend fun getJids(): List<String>
}
