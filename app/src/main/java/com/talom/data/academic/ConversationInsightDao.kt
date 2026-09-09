package com.talom.data.academic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationInsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ConversationInsightEntity>)

    @Query("SELECT * FROM conversation_insights WHERE stableId = :stableId LIMIT 1")
    suspend fun findById(stableId: String): ConversationInsightEntity?

    @Query("DELETE FROM conversation_insights WHERE sourceJid = :jid AND sourceMessageId IN (:messageIds)")
    suspend fun deleteBySource(jid: String, messageIds: List<Long>)

    @Query("SELECT * FROM conversation_insights ORDER BY storedAtMillis DESC")
    fun observeAll(): Flow<List<ConversationInsightEntity>>
}
