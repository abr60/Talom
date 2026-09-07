package com.talom.core.ai

import com.talom.core.source.SourceMessage

enum class AiMode {
    DISABLED,
    LOCAL,
    CLOUD,
}

data class AiProviderConfig(
    val mode: AiMode = AiMode.DISABLED,
    val providerId: String? = null,
    val modelId: String? = null,
    val endpoint: String? = null,
)

data class AcademicExtractionRequest(
    val messages: List<SourceMessage>,
    val schemaVersion: Int = 1,
)

data class AcademicExtractionResult(
    val items: List<AcademicItem>,
    val insights: List<ConversationInsight> = emptyList(),
    val providerId: String,
    val modelId: String,
    val schemaVersion: Int,
)

data class ConversationInsight(
    val stableId: String,
    val type: InsightType,
    val title: String,
    val details: String?,
    val sourceJid: String,
    val sourceMessageId: Long,
    val confidence: Float,
    val extractionVersion: Int,
)

enum class InsightType {
    PERSONAL_REMINDER,
    PLAN,
    FAMILY,
    FRIEND,
    GENERAL,
}

sealed interface AiProviderResult {
    data class Success(val result: AcademicExtractionResult) : AiProviderResult

    data class Failure(
        val code: Code,
        val message: String,
        val cause: Throwable? = null,
    ) : AiProviderResult {
        enum class Code {
            DISABLED,
            UNAVAILABLE,
            INVALID_CONFIGURATION,
            INVALID_OUTPUT,
            PROVIDER_ERROR,
        }
    }
}

interface AiProvider {
    val config: AiProviderConfig

    suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult
}

class DisabledAiProvider(
    override val config: AiProviderConfig = AiProviderConfig(),
) : AiProvider {
    override suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult = AiProviderResult.Failure(
        code = AiProviderResult.Failure.Code.DISABLED,
        message = "AI processing is disabled. Choose a provider explicitly.",
    )
}
