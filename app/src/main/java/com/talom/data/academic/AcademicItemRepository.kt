package com.talom.data.academic

import com.talom.core.ai.AcademicExtractionResult

class AcademicItemRepository(
    private val dao: AcademicItemDao,
) {
    suspend fun persist(
        result: AcademicExtractionResult,
        receivedAtByMessageId: Map<Long, Long> = emptyMap(),
    ) {
        dao.upsertAll(
            result.items.map { item ->
                val existing = dao.findById(item.stableId)
                val received = receivedAtByMessageId[item.sourceMessageId]
                    ?: existing?.receivedAtMillis
                val sanitizedDue = sanitizeDueAtMillis(item.dueAtMillis, received)
                AcademicItemEntity.fromDomain(
                    item = item.copy(dueAtMillis = sanitizedDue),
                    providerId = result.providerId,
                    modelId = result.modelId,
                    done = existing?.done ?: false,
                    submittedAtMillis = existing?.submittedAtMillis,
                    receivedAtMillis = received,
                )
            },
        )
    }

    suspend fun markDone(stableId: String, submittedAtMillis: Long = System.currentTimeMillis()) {
        dao.markDone(stableId, submittedAtMillis)
    }

    /**
     * Drop items that no longer make sense to surface.
     *
     * Important: do NOT prune freshly stored rows solely because the model
     * hallucinated an ancient dueAtMillis — that caused assignments to flash
     * then vanish right after pull.
     *
     * - Submitted: deleted 7 days after submittedAtMillis.
     * - Time-bound pending: due older than 7 days AND stored older than 3 days.
     * - Announcements/cancellations: 14 days after stored.
     */
    suspend fun pruneStale(now: Long = System.currentTimeMillis()) {
        val oneDayMs = 24L * 60 * 60 * 1000
        val threeDaysMs = 3L * oneDayMs
        val sevenDaysMs = 7L * oneDayMs
        val fourteenDaysMs = 14L * oneDayMs
        val timeBound = listOf(
            "CLASS_SCHEDULE", "DEADLINE", "ASSIGNMENT", "EXAM",
            "CLASS_TEST", "PRESENTATION", "VIVA", "INTERVIEW", "PRACTICAL",
        )
        val timeless = listOf("ANNOUNCEMENT", "CANCELLATION")
        dao.deleteSubmittedOlderThan(now - sevenDaysMs)
        dao.deleteStaleTimeBound(
            dueCutoff = now - sevenDaysMs,
            storedCutoff = now - threeDaysMs,
            types = timeBound,
        )
        dao.deleteOldAnnouncements(now - fourteenDaysMs, timeless)
    }

    suspend fun clearAll() = dao.clearAll()

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Null out absurd past dues (e.g. year 2023 on a 2026 message) so prune
         * does not wipe useful freshly-extracted assignments.
         */
        fun sanitizeDueAtMillis(dueAtMillis: Long?, receivedAtMillis: Long?): Long? {
            val due = dueAtMillis ?: return null
            val received = receivedAtMillis ?: return due
            return if (due < received - 180L * DAY_MS) null else due
        }
    }
}

class ConversationInsightRepository(
    private val dao: ConversationInsightDao,
) {
    suspend fun persist(
        result: AcademicExtractionResult,
        receivedAtByMessageId: Map<Long, Long> = emptyMap(),
    ) {
        dao.upsertAll(
            result.insights.map { insight ->
                val existing = dao.findById(insight.stableId)
                ConversationInsightEntity.fromDomain(
                    insight = insight,
                    providerId = result.providerId,
                    modelId = result.modelId,
                    receivedAtMillis = receivedAtByMessageId[insight.sourceMessageId]
                        ?: existing?.receivedAtMillis,
                )
            },
        )
    }
}
