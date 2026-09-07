package com.talom.data.whatsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WhatsAppCursorDao {
    @Query("SELECT * FROM whatsapp_cursors WHERE sourceId = :sourceId")
    suspend fun find(sourceId: String): WhatsAppCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(cursor: WhatsAppCursorEntity)
}
