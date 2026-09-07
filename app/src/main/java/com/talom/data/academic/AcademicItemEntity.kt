package com.talom.data.academic

import androidx.room.Entity
import com.talom.core.ai.AcademicItem
import com.talom.core.ai.AcademicItemType

@Entity(tableName = "academic_items")
data class AcademicItemEntity(
    @androidx.room.PrimaryKey val stableId: String,
    val type: String,
    val title: String,
    val details: String?,
    val subject: String?,
    val dueAtMillis: Long?,
    val sourceJid: String,
    val sourceMessageId: Long,
    val confidence: Float,
    val extractionVersion: Int,
    val providerId: String,
    val modelId: String,
    val storedAtMillis: Long,
) {
    fun toDomain(): AcademicItem = AcademicItem(
        stableId = stableId,
        type = AcademicItemType.valueOf(type),
        title = title,
        details = details,
        subject = subject,
        dueAtMillis = dueAtMillis,
        sourceJid = sourceJid,
        sourceMessageId = sourceMessageId,
        confidence = confidence,
        extractionVersion = extractionVersion,
    )

    companion object {
        fun fromDomain(
            item: AcademicItem,
            providerId: String,
            modelId: String,
            storedAtMillis: Long = System.currentTimeMillis(),
        ) = AcademicItemEntity(
            stableId = item.stableId,
            type = item.type.name,
            title = item.title,
            details = item.details,
            subject = item.subject,
            dueAtMillis = item.dueAtMillis,
            sourceJid = item.sourceJid,
            sourceMessageId = item.sourceMessageId,
            confidence = item.confidence,
            extractionVersion = item.extractionVersion,
            providerId = providerId,
            modelId = modelId,
            storedAtMillis = storedAtMillis,
        )
    }
}
