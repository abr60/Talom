package com.talom.data.source

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PullLogDao {
    @Query("SELECT * FROM pull_log WHERE sourceId = :sourceId ORDER BY startedAtMillis DESC LIMIT :limit")
    fun observeRecent(sourceId: String, limit: Int = 10): Flow<List<PullLogEntity>>

    @Query("SELECT * FROM pull_log WHERE sourceId = :sourceId ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun latest(sourceId: String): PullLogEntity?

    @Insert
    suspend fun insert(log: PullLogEntity)
}
