package com.talom.source.whatsapp

import com.talom.core.source.MessageCursor
import com.talom.core.source.MessageSource
import com.talom.core.source.MessageSourceRequest
import com.talom.core.source.MessageSourceResult
import com.talom.data.whatsapp.WhatsAppCursorDao
import com.talom.data.whatsapp.WhatsAppCursorEntity
import com.talom.data.whatsapp.WhatsAppMessageDao
import com.talom.data.whatsapp.WhatsAppMessageEntity
import com.talom.data.source.SourceStatusDao
import com.talom.data.source.SourceStatusEntity
import com.talom.data.source.PullLogDao
import com.talom.data.source.PullLogEntity
import androidx.room.withTransaction
import com.talom.core.ai.AcademicExtractionService
import com.talom.core.ai.AiProviderResult
import com.talom.data.academic.AcademicItemRepository
import com.talom.data.academic.ConversationInsightRepository

data class WhatsAppPullResult(
    val extractedCount: Int,
    val snapshotTimestampMillis: Long,
    val nextCursor: MessageCursor?,
    val academicItemCount: Int = 0,
    val insightCount: Int = 0,
)

class WhatsAppPullRepository(
    private val source: MessageSource,
    private val whitelistDao: com.talom.data.whatsapp.WhatsAppWhitelistDao,
    private val cursorDao: WhatsAppCursorDao,
    private val messageDao: WhatsAppMessageDao,
    private val statusDao: SourceStatusDao,
    private val pullLogDao: PullLogDao,
    private val database: com.talom.data.TalomDatabase,
    private val academicExtractionService: AcademicExtractionService? = null,
    private val academicItemRepository: AcademicItemRepository? = null,
    private val conversationInsightRepository: ConversationInsightRepository? = null,
    private val sourceId: String = SOURCE_ID,
    private val messageWindowDays: () -> Int = { 30 },
) {
    suspend fun pull(limit: Int = 100): Result<WhatsAppPullResult> {
        val startedAt = System.currentTimeMillis()
        val jids = whitelistDao.getJids().toSet()
        if (jids.isEmpty()) {
            pullLogDao.insert(
                PullLogEntity(
                    sourceId = sourceId,
                    startedAtMillis = startedAt,
                    completedAtMillis = System.currentTimeMillis(),
                    state = "SKIPPED",
                    itemCount = 0,
                    detail = "No WhatsApp JID is configured.",
                ),
            )
            return Result.failure(IllegalStateException("Add at least one WhatsApp JID first."))
        }

        var totalExtracted = 0
        var latestSnapshot = 0L
        var latestCursor: MessageCursor? = null
        var academicCount = 0
        var insightCount = 0
        for (jid in jids) {
            val storedCursor = cursorDao.find(jid)?.let {
                MessageCursor(it.timestampMillis, it.messageId)
            }
            when (
                val result = source.extract(
                    MessageSourceRequest(
                        allowedJids = setOf(jid),
                        cursor = storedCursor,
                        limit = limit,
                        includeText = academicExtractionService != null,
                    ),
                )
            ) {
                is MessageSourceResult.Failure -> {
                    pullLogDao.insert(
                        PullLogEntity(
                            sourceId = sourceId,
                            startedAtMillis = startedAt,
                            completedAtMillis = System.currentTimeMillis(),
                            state = "FAILED",
                            itemCount = totalExtracted,
                            detail = result.message,
                        ),
                    )
                    return Result.failure(
                        IllegalStateException(
                            result.cause?.message?.let { "${result.message} ($it)" } ?: result.message,
                            result.cause,
                        ),
                    )
                }

                is MessageSourceResult.Success -> {
                    latestSnapshot = maxOf(latestSnapshot, result.batch.snapshotTimestampMillis)
                    val windowCutoff = System.currentTimeMillis() - messageWindowDays() * 24L * 60 * 60 * 1000
                    val windowedMessages = result.batch.messages.filter { it.timestampMillis >= windowCutoff }
                    if (academicExtractionService != null && windowedMessages.isNotEmpty()) {
                        for (batch in windowedMessages.chunked(AI_BATCH_SIZE)) {
                            when (val extraction = academicExtractionService.extract(batch)) {
                                is AiProviderResult.Failure -> {
                                    return Result.failure(
                                        IllegalStateException(extraction.message, extraction.cause),
                                    )
                                }
                                is AiProviderResult.Success -> {
                                    academicCount += extraction.result.items.size
                                    insightCount += extraction.result.insights.size
                                    if (academicItemRepository != null) {
                                        database.withTransaction {
                                            academicItemRepository.persist(extraction.result)
                                            conversationInsightRepository?.persist(extraction.result)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    result.batch.nextCursor?.let {
                        database.withTransaction {
                            messageDao.insertAll(
                                result.batch.messages.map { message ->
                                    WhatsAppMessageEntity(
                                        messageId = message.messageId,
                                        chatId = message.chatId,
                                        jid = message.jid,
                                        chatSubject = message.chatSubject,
                                        fromMe = message.fromMe,
                                        timestampMillis = message.timestampMillis,
                                        messageType = message.messageType,
                                        hasText = message.text != null,
                                    )
                                },
                            )
                            cursorDao.save(
                                WhatsAppCursorEntity(
                                    sourceId = jid,
                                    timestampMillis = it.timestampMillis,
                                    messageId = it.messageId,
                                ),
                            )
                        }
                        totalExtracted += result.batch.messages.size
                        latestCursor = it
                    }
                }
            }
        }
        statusDao.save(
            SourceStatusEntity(
                sourceId = sourceId,
                state = "READY",
                lastSuccessfulPullMillis = System.currentTimeMillis(),
                lastSnapshotMillis = latestSnapshot,
                detail = "$totalExtracted new messages",
            ),
        )
        pullLogDao.insert(
            PullLogEntity(
                sourceId = sourceId,
                startedAtMillis = startedAt,
                completedAtMillis = System.currentTimeMillis(),
                state = "SUCCESS",
                itemCount = totalExtracted,
                detail = "$totalExtracted new messages; $academicCount academic items; " +
                    "$insightCount personal/general insights",
                academicItemCount = academicCount,
                insightCount = insightCount,
            ),
        )
        // Sweep out stale items so the DB never accumulates old class schedules,
        // past deadlines, or ancient announcements/cancellations.
        academicItemRepository?.pruneStale()
        return Result.success(
            WhatsAppPullResult(
                extractedCount = totalExtracted,
                snapshotTimestampMillis = latestSnapshot,
                nextCursor = latestCursor,
                academicItemCount = academicCount,
                insightCount = insightCount,
            ),
        )
    }

    companion object {
        const val SOURCE_ID = "whatsapp"
        private const val AI_BATCH_SIZE = 4
    }
}
