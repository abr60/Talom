package com.talom.core.ai

object ConversationInsightValidator {
    fun validateAll(
        insights: List<ConversationInsight>,
        expectedVersion: Int,
    ): ValidationResult<ConversationInsight> {
        val errors = mutableListOf<String>()
        val valid = mutableListOf<ConversationInsight>()
        for (insight in insights) {
            val reason = rejectReason(insight, expectedVersion)
            if (reason != null) {
                errors += reason
            } else {
                valid += insight
            }
        }
        return ValidationResult(
            valid = valid.distinctBy { it.stableId },
            errors = errors,
        )
    }

    private fun rejectReason(insight: ConversationInsight, expectedVersion: Int): String? = when {
        insight.extractionVersion != expectedVersion ->
            "drop insight: version mismatch '${insight.title.take(40)}'"
        insight.stableId.isBlank() || insight.stableId == "string" || insight.stableId.contains("string|null") ->
            "drop insight: bad stableId '${insight.title.take(40)}'"
        insight.title.isBlank() || insight.title == "string" ->
            "drop insight: blank title"
        insight.sourceJid.isBlank() || insight.sourceJid == "string" ->
            "drop insight: bad sourceJid '${insight.title.take(40)}'"
        insight.sourceMessageId < 0 ->
            "drop insight: bad sourceMessageId '${insight.title.take(40)}'"
        insight.confidence !in 0f..1f ->
            "drop insight: confidence ${insight.confidence} for '${insight.title.take(40)}'"
        else -> null
    }
}
