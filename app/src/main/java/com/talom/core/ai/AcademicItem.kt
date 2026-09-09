package com.talom.core.ai

data class ValidationResult<T>(
    val valid: List<T>,
    val errors: List<String>,
) {
    val droppedCount: Int get() = errors.size
    val allValid: Boolean get() = errors.isEmpty()
}

enum class AcademicItemType {
    CLASS_SCHEDULE,
    ASSIGNMENT,
    EXAM,
    DEADLINE,
    ANNOUNCEMENT,
    CANCELLATION,
    CLASS_TEST,
    PRESENTATION,
    VIVA,
    INTERVIEW,
    PRACTICAL,
}

data class AcademicItem(
    val stableId: String,
    val type: AcademicItemType,
    val title: String,
    val details: String?,
    val subject: String?,
    val dueAtMillis: Long?,
    val sourceJid: String,
    val sourceMessageId: Long,
    val confidence: Float,
    val extractionVersion: Int,
)

object AcademicItemValidator {
    fun validateAll(
        items: List<AcademicItem>,
        expectedVersion: Int,
    ): ValidationResult<AcademicItem> {
        val errors = mutableListOf<String>()
        val valid = mutableListOf<AcademicItem>()
        for (item in items) {
            val reason = rejectReason(item, expectedVersion)
            if (reason != null) {
                errors += reason
            } else {
                valid += item
                // Soft warning: assignment without due is kept (was previously hard-dropped).
                if (item.type == AcademicItemType.ASSIGNMENT && item.dueAtMillis == null) {
                    errors += "warn: ASSIGNMENT '${item.title.take(40)}' missing dueAtMillis (kept)"
                }
            }
        }
        return ValidationResult(
            valid = valid.distinctBy { it.stableId },
            errors = errors,
        )
    }

    private fun rejectReason(item: AcademicItem, expectedVersion: Int): String? = when {
        item.extractionVersion != expectedVersion ->
            "drop: version mismatch for '${item.title.take(40)}' (got ${item.extractionVersion})"
        item.stableId.isBlank() || item.stableId == "string" || item.stableId.contains("string|null") ->
            "drop: bad stableId for '${item.title.take(40)}'"
        item.title.isBlank() || item.title == "string" ->
            "drop: blank/placeholder title (${item.stableId.take(48)})"
        item.sourceJid.isBlank() || item.sourceJid == "string" ->
            "drop: bad sourceJid for '${item.title.take(40)}'"
        item.sourceMessageId < 0 ->
            "drop: bad sourceMessageId for '${item.title.take(40)}'"
        item.confidence !in 0f..1f ->
            "drop: confidence ${item.confidence} out of range for '${item.title.take(40)}'"
        else -> null
    }
}
