package com.talom.data.source

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceStatusDao {
    @Query("SELECT * FROM source_status WHERE sourceId = :sourceId")
    fun observe(sourceId: String): Flow<SourceStatusEntity?>

    @Query("SELECT * FROM source_status WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): SourceStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(status: SourceStatusEntity)
}
