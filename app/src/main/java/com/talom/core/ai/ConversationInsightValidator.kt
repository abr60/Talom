package com.talom.core.ai

object ConversationInsightValidator {
    fun validateAll(
        insights: List<ConversationInsight>,
        expectedVersion: Int,
    ): Result<List<ConversationInsight>> {
        val valid = insights.filter {
            it.extractionVersion == expectedVersion &&
                it.stableId.isNotBlank() &&
                it.title.isNotBlank() &&
                it.sourceJid.isNotBlank() &&
                it.sourceMessageId >= 0 &&
                it.confidence in 0f..1f
        }
        return Result.success(valid.distinctBy { it.stableId })
    }
}
