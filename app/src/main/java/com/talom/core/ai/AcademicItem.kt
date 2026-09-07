package com.talom.core.ai

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
    ): Result<List<AcademicItem>> {
        val valid = items.filter { item ->
            item.extractionVersion == expectedVersion &&
                item.stableId.isNotBlank() &&
                item.title.isNotBlank() &&
                item.sourceJid.isNotBlank() &&
                item.sourceMessageId >= 0 &&
                item.confidence in 0f..1f &&
                (item.type != AcademicItemType.ASSIGNMENT || item.dueAtMillis != null)
        }
        return Result.success(valid.distinctBy { it.stableId })
    }
}
