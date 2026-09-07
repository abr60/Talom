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

    @Query("SELECT * FROM conversation_insights ORDER BY storedAtMillis DESC")
    fun observeAll(): Flow<List<ConversationInsightEntity>>
}
