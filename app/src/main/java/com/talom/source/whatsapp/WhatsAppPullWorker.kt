package com.talom.source.whatsapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.talom.core.ai.AcademicExtractionService
import com.talom.core.ai.AiMode
import com.talom.core.ai.AiPreferences
import com.talom.core.ai.ConfiguredAiProvider
import com.talom.data.TalomDatabaseProvider
import com.talom.data.academic.AcademicItemRepository
import com.talom.data.academic.ConversationInsightRepository

class WhatsAppPullWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = TalomDatabaseProvider.get(applicationContext)
        val preferences = AiPreferences(applicationContext)
        val config = preferences.config()
        val aiService = if (
            (config.mode == AiMode.CLOUD &&
                preferences.cloudConsent() &&
                !preferences.apiKey().isNullOrBlank()) ||
            (config.mode == AiMode.LOCAL && preferences.localConsent())
        ) {
            AcademicExtractionService(
                ConfiguredAiProvider.create(config, preferences.apiKey()),
            )
        } else {
            null
        }
        val result = WhatsAppPullRepository(
            source = WhatsAppMessageSource(RootWhatsAppSnapshotProvider(applicationContext)),
            whitelistDao = database.whatsappWhitelistDao(),
            cursorDao = database.whatsappCursorDao(),
            messageDao = database.whatsappMessageDao(),
            statusDao = database.sourceStatusDao(),
            pullLogDao = database.pullLogDao(),
            database = database,
            academicExtractionService = aiService,
            academicItemRepository = AcademicItemRepository(database.academicItemDao()),
            conversationInsightRepository = ConversationInsightRepository(database.conversationInsightDao()),
            messageWindowDays = { preferences.messageWindowDays() },
        ).pull()
        if (result.isSuccess) {
            result.getOrThrow().let { pull ->
                TalomNotification.notifyPullResults(
                    applicationContext,
                    pull.academicItemCount,
                    pull.insightCount,
                )
            }
            return Result.success()
        }
        return Result.retry()
    }

    companion object {
        const val WORK_NAME = "talom-whatsapp-periodic-pull"
    }
}
