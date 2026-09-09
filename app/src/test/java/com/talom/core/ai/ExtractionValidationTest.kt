package com.talom.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionValidationTest {
    @Test
    fun academicValidationKeepsValidItemsAndDeduplicatesStableIds() {
        val valid = academic("same")
        val duplicate = academic("same", title = "duplicate")
        val assignmentNoDue = academic(
            stableId = "assignment",
            type = AcademicItemType.ASSIGNMENT,
            dueAtMillis = null,
        )
        val invalidConfidence = academic("confidence", confidence = 1.1f)
        val wrongVersion = academic("version", extractionVersion = 2)

        val result = AcademicItemValidator.validateAll(
            listOf(valid, duplicate, assignmentNoDue, invalidConfidence, wrongVersion),
            expectedVersion = 1,
        )

        assertEquals(listOf("same", "assignment"), result.valid.map { it.stableId })
        assertEquals("valid", result.valid.first().title)
        assertTrue(result.errors.any { it.startsWith("warn:") })
        assertTrue(result.errors.any { it.startsWith("drop:") })
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
        )

        assertEquals(listOf("same"), result.valid.map { it.stableId })
        assertEquals("valid", result.valid.single().title)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun bengaliTokenEstimateIsHigherThanEnglish() {
        val english = "Hello this is a short English message about class tomorrow"
        val bengali = "আগামীকাল সকালে মাইক্রোইকোনমিক্স ক্লাস হবে রুম তিনশো দুই"
        assertTrue(AiPrompt.estimateTokens(bengali) > AiPrompt.estimateTokens(english))
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
