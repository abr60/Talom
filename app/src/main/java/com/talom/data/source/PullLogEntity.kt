package com.talom.data.source

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pull_log")
data class PullLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val state: String,
    val itemCount: Int,
    val detail: String?,
    val academicItemCount: Int = 0,
    val insightCount: Int = 0,
    val skippedCount: Int = 0,
)
