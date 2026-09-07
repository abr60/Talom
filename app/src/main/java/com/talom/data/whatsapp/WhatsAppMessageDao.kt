package com.talom.data.whatsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface WhatsAppMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<WhatsAppMessageEntity>)
}
