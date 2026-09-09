package com.talom.data.academic

import androidx.room.Entity
import com.talom.core.ai.ConversationInsight
import com.talom.core.ai.InsightType

@Entity(tableName = "conversation_insights")
data class ConversationInsightEntity(
    @androidx.room.PrimaryKey val stableId: String,
    val type: String,
    val title: String,
    val details: String?,
    val sourceJid: String,
    val sourceMessageId: Long,
    val confidence: Float,
    val extractionVersion: Int,
    val providerId: String,
    val modelId: String,
    val storedAtMillis: Long,
    val receivedAtMillis: Long? = null,
) {
    fun toDomain() = ConversationInsight(
        stableId, InsightType.valueOf(type), title, details, sourceJid,
        sourceMessageId, confidence, extractionVersion,
    )

    companion object {
        fun fromDomain(
            insight: ConversationInsight,
            providerId: String,
            modelId: String,
            receivedAtMillis: Long? = null,
        ) = ConversationInsightEntity(
            insight.stableId, insight.type.name, insight.title, insight.details,
            insight.sourceJid, insight.sourceMessageId, insight.confidence,
            insight.extractionVersion, providerId, modelId, System.currentTimeMillis(),
            receivedAtMillis = receivedAtMillis,
        )
    }
}
