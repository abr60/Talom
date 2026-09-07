package com.talom.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtractionValidationTest {
    @Test
    fun academicValidationKeepsValidItemsAndDeduplicatesStableIds() {
        val valid = academic("same")
        val duplicate = academic("same", title = "duplicate")
        val invalidAssignment = academic(
            stableId = "assignment",
            type = AcademicItemType.ASSIGNMENT,
            dueAtMillis = null,
        )
        val invalidConfidence = academic("confidence", confidence = 1.1f)
        val wrongVersion = academic("version", extractionVersion = 2)

        val result = AcademicItemValidator.validateAll(
            listOf(valid, duplicate, invalidAssignment, invalidConfidence, wrongVersion),
            expectedVersion = 1,
        ).getOrThrow()

        assertEquals(listOf("same"), result.map { it.stableId })
        assertEquals("valid", result.single().title)
    }

    @Test
    fun insightValidationFiltersMalformedRecordsAndDeduplicates() {
        val valid = insight("same")
        val duplicate = insight("same", title = "duplicate")
        val invalid = insight("invalid", sourceMessageId = -1)
        val wrongVersion = insight("version", extractionVersion = 2)

        val result = ConversationInsightValidator.validateAll(
            listOf(valid, duplicate, invalid, wrongVersion),
            expectedVersion = 1,
        ).getOrThrow()

        assertEquals(listOf("same"), result.map { it.stableId })
        assertEquals("valid", result.single().title)
    }

    private fun academic(
        stableId: String,
        title: String = "valid",
        type: AcademicItemType = AcademicItemType.CLASS_SCHEDULE,
        dueAtMillis: Long? = 123L,
        confidence: Float = 0.9f,
        extractionVersion: Int = 1,
    ) = AcademicItem(
        stableId = stableId,
        type = type,
        title = title,
        details = null,
        subject = null,
        dueAtMillis = dueAtMillis,
        sourceJid = "contact@s.whatsapp.net",
        sourceMessageId = 1L,
        confidence = confidence,
        extractionVersion = extractionVersion,
    )

    private fun insight(
        stableId: String,
        title: String = "valid",
        sourceMessageId: Long = 1L,
        extractionVersion: Int = 1,
    ) = ConversationInsight(
        stableId = stableId,
        type = InsightType.GENERAL,
        title = title,
        details = null,
        sourceJid = "contact@s.whatsapp.net",
        sourceMessageId = sourceMessageId,
        confidence = 0.9f,
        extractionVersion = extractionVersion,
    )
}
