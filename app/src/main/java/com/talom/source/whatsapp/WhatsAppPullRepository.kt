package com.talom.source.whatsapp

import androidx.room.withTransaction
import com.talom.core.ai.AcademicExtractionService
import com.talom.core.ai.AiMode
import com.talom.core.ai.AiPrompt
import com.talom.core.ai.AiProviderResult
import com.talom.core.source.MessageCursor
import com.talom.core.source.MessageSource
import com.talom.core.source.MessageSourceRequest
import com.talom.core.source.MessageSourceResult
import com.talom.data.academic.AcademicItemRepository
import com.talom.data.academic.ConversationInsightRepository
import com.talom.data.source.PullLogDao
import com.talom.data.source.PullLogEntity
import com.talom.data.source.SourceStatusDao
import com.talom.data.source.SourceStatusEntity
import com.talom.data.whatsapp.WhatsAppCursorDao
import com.talom.data.whatsapp.WhatsAppCursorEntity
import com.talom.data.whatsapp.WhatsAppMessageDao
import com.talom.data.whatsapp.WhatsAppMessageEntity
import kotlinx.coroutines.delay

data class WhatsAppPullResult(
    val extractedCount: Int,
    val snapshotTimestampMillis: Long,
    val nextCursor: MessageCursor?,
    val academicItemCount: Int = 0,
    val insightCount: Int = 0,
    val force: Boolean = false,
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
    suspend fun pull(limit: Int = 100, force: Boolean = false): Result<WhatsAppPullResult> {
        val startedAt = System.currentTimeMillis()
        val jids = whitelistDao.getJids().toSet()
        if (jids.isEmpty()) {
            val detail = "No WhatsApp JID is configured."
            pullLogDao.insert(
                PullLogEntity(
                    sourceId = sourceId,
                    startedAtMillis = startedAt,
                    completedAtMillis = System.currentTimeMillis(),
                    state = "SKIPPED",
                    itemCount = 0,
                    detail = detail,
                ),
            )
            return Result.failure(IllegalStateException(detail))
        }
        if (force && academicExtractionService == null) {
            val detail = "Force re-extract requires AI processing to be enabled (turn on AI, add endpoint/key/model, save, then try again)."
            pullLogDao.insert(
                PullLogEntity(
                    sourceId = sourceId,
                    startedAtMillis = startedAt,
                    completedAtMillis = System.currentTimeMillis(),
                    state = "SKIPPED",
                    itemCount = 0,
                    detail = detail,
                ),
            )
            return Result.failure(IllegalStateException(detail))
        }

        var totalExtracted = 0
        var latestSnapshot = 0L
        var latestCursor: MessageCursor? = null
        var academicCount = 0
        var insightCount = 0
        var validationDropCount = 0
        val validationNotes = mutableListOf<String>()
        val partialFailures = mutableListOf<String>()
        val isLocal = academicExtractionService?.provider?.config?.mode == AiMode.LOCAL
        val windowDays = messageWindowDays()

        for (jid in jids) {
            val windowCutoff = System.currentTimeMillis() - windowDays * 24L * 60 * 60 * 1000
            // Force mode: synthetic cursor at window start, paginate; normal: stored cursor, single page.
            var pageCursor: MessageCursor? = if (force) MessageCursor(windowCutoff, 0L)
            else cursorDao.find(jid)?.let { MessageCursor(it.timestampMillis, it.messageId) }

            var jidPageDone = false
            var jidHadAnyPage = false
            while (!jidPageDone) {
                val result = source.extract(
                    MessageSourceRequest(
                        allowedJids = setOf(jid),
                        cursor = pageCursor,
                        limit = limit,
                        includeText = academicExtractionService != null,
                    ),
                )
                when (result) {
                    is MessageSourceResult.Failure -> {
                        val hint = when (result.code) {
                            MessageSourceResult.Failure.Code.SNAPSHOT_FAILED,
                            MessageSourceResult.Failure.Code.SOURCE_UNAVAILABLE ->
                                " — is the device rooted and WhatsApp installed? Grant root when prompted."
                            MessageSourceResult.Failure.Code.UNSUPPORTED_SCHEMA ->
                                " — WhatsApp database schema changed; update Talom or clear WhatsApp data."
                            else -> ""
                        }
                        val detail = "${result.message}$hint"
                        pullLogDao.insert(
                            PullLogEntity(
                                sourceId = sourceId,
                                startedAtMillis = startedAt,
                                completedAtMillis = System.currentTimeMillis(),
                                state = "FAILED",
                                itemCount = totalExtracted,
                                detail = detail,
                            ),
                        )
                        return Result.failure(
                            IllegalStateException(
                                result.cause?.message?.let { "$detail ($it)" } ?: detail,
                                result.cause,
                            ),
                        )
                    }
                    is MessageSourceResult.Success -> {
                        jidHadAnyPage = true
                        latestSnapshot = maxOf(latestSnapshot, result.batch.snapshotTimestampMillis)
                        val windowed = result.batch.messages.filter { it.timestampMillis >= windowCutoff }
                        val existingIds = if (force) emptySet() else runCatching { messageDao.getMessageIdsWithTextForJid(jid).toSet() }.getOrDefault(emptySet())
                        val newMessages = if (force) windowed else if (existingIds.isEmpty()) windowed else windowed.filter { it.messageId !in existingIds }

                        if (newMessages.isEmpty()) {
                            result.batch.nextCursor?.let { latestCursor = if (force) latestCursor else it }
                            // Force pagination: keep paging even when this page contributed nothing.
                            if (force) {
                                val next = result.batch.nextCursor
                                if (next == null || next == pageCursor) jidPageDone = true
                                else pageCursor = next
                                continue
                            } else {
                                jidPageDone = true
                                continue
                            }
                        }

                        val messagesForAi = if (academicExtractionService != null) {
                            newMessages.filter { m -> !m.text.isNullOrBlank() && !AiPrompt.isNoise(m.text!!) }
                        } else emptyList()

                        var jidExtracted = 0
                        var jidLastCursor: MessageCursor? = null

                        if (academicExtractionService != null && messagesForAi.isNotEmpty()) {
                            // Delete prior items for these messages (force re-extract idempotency when LLM stableIds drift).
                            val idsForPage = newMessages.map { it.messageId }
                            if (force) {
                                // Best-effort clean of previous outputs for these source messages.
                                runCatching { database.academicItemDao().deleteBySource(jid, idsForPage) }
                                runCatching { database.conversationInsightDao().deleteBySource(jid, idsForPage) }
                            }
                            val batches = AiPrompt.chunkByTokens(messagesForAi)
                            var failed = false
                            for ((idx, batch) in batches.withIndex()) {
                                if (idx > 0 && !isLocal) delay(PACING_DELAY_MS)
                                when (val extraction = academicExtractionService.extract(batch)) {
                                    is AiProviderResult.Failure -> {
                                        val hint = " — check AI settings (mode, endpoint, API key, model) and internet; provider may be offline or rate-limited."
                                        partialFailures += "$jid batch ${idx + 1}/${batches.size}: ${extraction.message}$hint"
                                        failed = true
                                        break
                                    }
                                    is AiProviderResult.Success -> {
                                        academicCount += extraction.result.items.size
                                        insightCount += extraction.result.insights.size
                                        validationDropCount += extraction.result.droppedItemCount +
                                            extraction.result.droppedInsightCount
                                        extraction.result.validationErrors
                                            .asSequence()
                                            .filter { it.startsWith("drop") }
                                            .take(3)
                                            .forEach { validationNotes += it }
                                        val batchLast = batch.last()
                                        val batchCursor = MessageCursor(batchLast.timestampMillis, batchLast.messageId)
                                        val receivedAtByMessageId = batch.associate { it.messageId to it.timestampMillis }
                                        if (force) {
                                            database.withTransaction {
                                                if (academicItemRepository != null) {
                                                    academicItemRepository.persist(extraction.result, receivedAtByMessageId)
                                                    conversationInsightRepository?.persist(extraction.result, receivedAtByMessageId)
                                                }
                                                // Force: ingest messages but DO NOT advance cursor.
                                                messageDao.insertAll(
                                                    batch.map { m ->
                                                        WhatsAppMessageEntity(
                                                            messageId = m.messageId,
                                                            chatId = m.chatId,
                                                            jid = m.jid,
                                                            chatSubject = m.chatSubject,
                                                            fromMe = m.fromMe,
                                                            timestampMillis = m.timestampMillis,
                                                            messageType = m.messageType,
                                                            hasText = m.text != null,
                                                        )
                                                    },
                                                )
                                            }
                                        } else {
                                            database.withTransaction {
                                                if (academicItemRepository != null) {
                                                    academicItemRepository.persist(extraction.result, receivedAtByMessageId)
                                                    conversationInsightRepository?.persist(extraction.result, receivedAtByMessageId)
                                                }
                                                messageDao.insertAll(
                                                    batch.map { m ->
                                                        WhatsAppMessageEntity(
                                                            messageId = m.messageId,
                                                            chatId = m.chatId,
                                                            jid = m.jid,
                                                            chatSubject = m.chatSubject,
                                                            fromMe = m.fromMe,
                                                            timestampMillis = m.timestampMillis,
                                                            messageType = m.messageType,
                                                            hasText = m.text != null,
                                                        )
                                                    },
                                                )
                                                cursorDao.save(
                                                    WhatsAppCursorEntity(
                                                        sourceId = jid,
                                                        timestampMillis = batchCursor.timestampMillis,
                                                        messageId = batchCursor.messageId,
                                                    ),
                                                )
                                            }
                                        }
                                        jidLastCursor = batchCursor
                                        jidExtracted += batch.size
                                    }
                                }
                            }
                            if (!failed && !force) {
                                val tailCursor = result.batch.nextCursor
                                if (tailCursor != null && tailCursor != jidLastCursor) {
                                    val remaining = newMessages.filter { m ->
                                        jidLastCursor == null || m.timestampMillis > jidLastCursor.timestampMillis ||
                                            (m.timestampMillis == jidLastCursor.timestampMillis && m.messageId > jidLastCursor.messageId)
                                    }
                                    if (remaining.isNotEmpty()) {
                                        database.withTransaction {
                                            messageDao.insertAll(
                                                remaining.map { m ->
                                                    WhatsAppMessageEntity(
                                                        messageId = m.messageId,
                                                        chatId = m.chatId,
                                                        jid = m.jid,
                                                        chatSubject = m.chatSubject,
                                                        fromMe = m.fromMe,
                                                        timestampMillis = m.timestampMillis,
                                                        messageType = m.messageType,
                                                        hasText = m.text != null,
                                                    )
                                                },
                                            )
                                            cursorDao.save(
                                                WhatsAppCursorEntity(
                                                    sourceId = jid,
                                                    timestampMillis = tailCursor.timestampMillis,
                                                    messageId = tailCursor.messageId,
                                                ),
                                            )
                                        }
                                        jidLastCursor = tailCursor
                                        jidExtracted += remaining.size
                                    }
                                }
                            } else if (force && !failed) {
                                // Force: ingest any trailing windowed messages skipped by AI (blank/noise) without cursor update.
                                val tailCursor = result.batch.nextCursor
                                val remaining = newMessages.filter { m ->
                                    jidLastCursor == null || m.timestampMillis > jidLastCursor.timestampMillis ||
                                        (m.timestampMillis == jidLastCursor.timestampMillis && m.messageId > jidLastCursor.messageId)
                                }
                                if (remaining.isNotEmpty()) {
                                    database.withTransaction {
                                        messageDao.insertAll(
                                            remaining.map { m ->
                                                WhatsAppMessageEntity(
                                                    messageId = m.messageId,
                                                    chatId = m.chatId,
                                                    jid = m.jid,
                                                    chatSubject = m.chatSubject,
                                                    fromMe = m.fromMe,
                                                    timestampMillis = m.timestampMillis,
                                                    messageType = m.messageType,
                                                    hasText = m.text != null,
                                                )
                                            },
                                        )
                                    }
                                    jidExtracted += remaining.size
                                    // Keep jidLastCursor as tail for accounting only; don't persist as cursor.
                                    if (tailCursor != null) jidLastCursor = tailCursor
                                }
                            }
                        } else {
                            // No AI or nothing for AI — persist messages.
                            if (force) {
                                database.withTransaction {
                                    messageDao.insertAll(
                                        newMessages.map { m ->
                                            WhatsAppMessageEntity(
                                                messageId = m.messageId,
                                                chatId = m.chatId,
                                                jid = m.jid,
                                                chatSubject = m.chatSubject,
                                                fromMe = m.fromMe,
                                                timestampMillis = m.timestampMillis,
                                                messageType = m.messageType,
                                                hasText = m.text != null,
                                            )
                                        },
                                    )
                                }
                                jidLastCursor = result.batch.nextCursor
                                jidExtracted = newMessages.size
                            } else {
                                val tail = result.batch.nextCursor
                                if (tail != null) {
                                    database.withTransaction {
                                        messageDao.insertAll(
                                            newMessages.map { m ->
                                                WhatsAppMessageEntity(
                                                    messageId = m.messageId,
                                                    chatId = m.chatId,
                                                    jid = m.jid,
                                                    chatSubject = m.chatSubject,
                                                    fromMe = m.fromMe,
                                                    timestampMillis = m.timestampMillis,
                                                    messageType = m.messageType,
                                                    hasText = m.text != null,
                                                )
                                            },
                                        )
                                        cursorDao.save(
                                            WhatsAppCursorEntity(
                                                sourceId = jid,
                                                timestampMillis = tail.timestampMillis,
                                                messageId = tail.messageId,
                                            ),
                                        )
                                    }
                                    jidLastCursor = tail
                                    jidExtracted = newMessages.size
                                }
                            }
                        }

                        totalExtracted += jidExtracted
                        // LatestCursor: only advance for incremental; force leaves it unchanged.
                        if (!force) jidLastCursor?.let { latestCursor = it }

                        // Pagination control
                        if (force) {
                            val next = result.batch.nextCursor
                            if (next == null || next == pageCursor || result.batch.messages.isEmpty()) jidPageDone = true
                            else pageCursor = next
                        } else {
                            jidPageDone = true
                        }
                    }
                }
            }
            // Edge: force mode with zero pages (empty snapshot) — already handled via first page empty loop exit.
            if (!jidHadAnyPage) {
                // No pages at all (e.g., snapshot with no messages for this jid) — nothing to do.
            }
        }

        if (totalExtracted == 0 && academicCount == 0 && partialFailures.isNotEmpty()) {
            val detail = partialFailures.joinToString("; ")
            pullLogDao.insert(
                PullLogEntity(
                    sourceId = sourceId,
                    startedAtMillis = startedAt,
                    completedAtMillis = System.currentTimeMillis(),
                    state = "FAILED",
                    itemCount = 0,
                    detail = detail,
                ),
            )
            return Result.failure(IllegalStateException(detail))
        }

        val successDetail = buildString {
            if (force) append("Re-extract ")
            append("$totalExtracted messages over last ${windowDays}d; $academicCount academic items; $insightCount personal/general insights")
            if (validationDropCount > 0) {
                append("; $validationDropCount dropped by validation")
                if (validationNotes.isNotEmpty()) {
                    append(" (${validationNotes.distinct().take(5).joinToString("; ")})")
                }
            }
            if (partialFailures.isNotEmpty()) append(" (partial: ${partialFailures.joinToString("; ")})")
        }
        val pullState = when {
            partialFailures.isNotEmpty() -> "PARTIAL"
            validationDropCount > 0 -> "PARTIAL"
            else -> "SUCCESS"
        }
        statusDao.save(
            SourceStatusEntity(
                sourceId = sourceId,
                state = "READY",
                lastSuccessfulPullMillis = System.currentTimeMillis(),
                lastSnapshotMillis = latestSnapshot,
                detail = (if (force) "Re-extract " else "") + "$totalExtracted messages over last ${windowDays}d",
            ),
        )
        pullLogDao.insert(
            PullLogEntity(
                sourceId = sourceId,
                startedAtMillis = startedAt,
                completedAtMillis = System.currentTimeMillis(),
                state = pullState,
                itemCount = totalExtracted,
                detail = successDetail,
                academicItemCount = academicCount,
                insightCount = insightCount,
                skippedCount = validationDropCount,
            ),
        )
        academicItemRepository?.pruneStale()
        return Result.success(
            WhatsAppPullResult(
                extractedCount = totalExtracted,
                snapshotTimestampMillis = latestSnapshot,
                nextCursor = latestCursor,
                academicItemCount = academicCount,
                insightCount = insightCount,
                force = force,
            ),
        )
    }

    companion object {
        const val SOURCE_ID = "whatsapp"
        private const val PACING_DELAY_MS = 350L
    }
}
