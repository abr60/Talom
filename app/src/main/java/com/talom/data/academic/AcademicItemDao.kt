package com.talom.data.academic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AcademicItemEntity>)

    @Query("SELECT * FROM academic_items ORDER BY dueAtMillis IS NULL, dueAtMillis, storedAtMillis DESC")
    fun observeAll(): Flow<List<AcademicItemEntity>>

    @Query("SELECT * FROM academic_items WHERE stableId = :stableId LIMIT 1")
    suspend fun findById(stableId: String): AcademicItemEntity?

    @Query(
        "UPDATE academic_items SET done = 1, submittedAtMillis = :submittedAtMillis WHERE stableId = :stableId",
    )
    suspend fun markDone(stableId: String, submittedAtMillis: Long)

    @Query(
        "DELETE FROM academic_items WHERE done = 1 AND submittedAtMillis IS NOT NULL AND submittedAtMillis < :cutoffMillis",
    )
    suspend fun deleteSubmittedOlderThan(cutoffMillis: Long)

    @Query(
        "DELETE FROM academic_items WHERE done = 0 AND dueAtMillis IS NOT NULL AND dueAtMillis < :dueCutoff AND storedAtMillis < :storedCutoff AND type IN (:types)",
    )
    suspend fun deleteStaleTimeBound(dueCutoff: Long, storedCutoff: Long, types: List<String>)

    @Query("DELETE FROM academic_items WHERE storedAtMillis < :cutoff AND type IN (:types)")
    suspend fun deleteOldAnnouncements(cutoff: Long, types: List<String>)

    @Query("DELETE FROM academic_items WHERE sourceJid = :jid AND sourceMessageId IN (:messageIds)")
    suspend fun deleteBySource(jid: String, messageIds: List<Long>)

    @Query("DELETE FROM academic_items")
    suspend fun clearAll()
}
