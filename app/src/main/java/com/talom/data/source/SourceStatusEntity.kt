package com.talom.data.source

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_status")
data class SourceStatusEntity(
    @PrimaryKey val sourceId: String,
    val state: String,
    val lastSuccessfulPullMillis: Long?,
    val lastSnapshotMillis: Long?,
    val detail: String?,
)
