package com.talom.data.academic

import com.talom.core.ai.AcademicExtractionResult

class AcademicItemRepository(
    private val dao: AcademicItemDao,
) {
    suspend fun persist(result: AcademicExtractionResult) {
        dao.upsertAll(
            result.items.map {
                AcademicItemEntity.fromDomain(
                    item = it,
                    providerId = result.providerId,
                    modelId = result.modelId,
                )
            },
        )
    }

    /**
     * Drop items that no longer make sense to surface.
     *
     * - Time-bound events (CLASS_SCHEDULE, DEADLINE, ASSIGNMENT, EXAM,
     *   CLASS_TEST, PRESENTATION, VIVA, INTERVIEW, PRACTICAL): deleted one
     *   day after their event time, so the user sees "today" and "yesterday"
     *   once and then it's gone.
     * - Timeless items (ANNOUNCEMENT, CANCELLATION): deleted 14 days after
     *   they were stored, since they lose relevance quickly.
     */
    suspend fun pruneStale(now: Long = System.currentTimeMillis()) {
        val oneDayMs = 24L * 60 * 60 * 1000
        val fourteenDaysMs = 14L * oneDayMs
        val timeBound = listOf(
            "CLASS_SCHEDULE", "DEADLINE", "ASSIGNMENT", "EXAM",
            "CLASS_TEST", "PRESENTATION", "VIVA", "INTERVIEW", "PRACTICAL",
        )
        val timeless = listOf("ANNOUNCEMENT", "CANCELLATION")
        dao.deleteStaleTimeBound(now - oneDayMs, timeBound)
        dao.deleteOldAnnouncements(now - fourteenDaysMs, timeless)
    }

    suspend fun clearAll() = dao.clearAll()
}

class ConversationInsightRepository(
    private val dao: ConversationInsightDao,
) {
    suspend fun persist(result: AcademicExtractionResult) {
        dao.upsertAll(
            result.insights.map {
                ConversationInsightEntity.fromDomain(it, result.providerId, result.modelId)
            },
        )
    }
}
