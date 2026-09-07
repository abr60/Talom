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

    @Query("DELETE FROM academic_items WHERE dueAtMillis IS NOT NULL AND dueAtMillis < :cutoff AND type IN (:types)")
    suspend fun deleteStaleTimeBound(cutoff: Long, types: List<String>)

    @Query("DELETE FROM academic_items WHERE storedAtMillis < :cutoff AND type IN (:types)")
    suspend fun deleteOldAnnouncements(cutoff: Long, types: List<String>)

    @Query("DELETE FROM academic_items")
    suspend fun clearAll()
}
