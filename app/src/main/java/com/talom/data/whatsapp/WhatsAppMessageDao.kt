package com.talom.data.whatsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WhatsAppMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<WhatsAppMessageEntity>)

    @Query("SELECT messageId FROM whatsapp_messages WHERE jid = :jid")
    suspend fun getMessageIdsForJid(jid: String): List<Long>

    @Query("SELECT messageId FROM whatsapp_messages WHERE jid = :jid AND hasText = 1")
    suspend fun getMessageIdsWithTextForJid(jid: String): List<Long>

    @Query("SELECT jid || '|' || CAST(messageId AS TEXT) FROM whatsapp_messages WHERE fromMe = 1")
    fun observeFromMeKeys(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT COUNT(*) FROM whatsapp_messages WHERE jid = :jid AND messageId = :messageId")
    suspend fun exists(jid: String, messageId: Long): Int
}
