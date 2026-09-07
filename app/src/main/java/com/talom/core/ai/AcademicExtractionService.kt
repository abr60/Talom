package com.talom.core.ai

import com.talom.core.source.SourceMessage

class AcademicExtractionService(
    private val provider: AiProvider,
    private val schemaVersion: Int = 1,
) {
    suspend fun extract(messages: List<SourceMessage>): AiProviderResult {
        if (messages.isEmpty()) {
            return AiProviderResult.Success(
                AcademicExtractionResult(
                    items = emptyList(),
                    insights = emptyList(),
                    providerId = provider.config.providerId ?: "none",
                    modelId = provider.config.modelId ?: "none",
                    schemaVersion = schemaVersion,
                ),
            )
        }

        return when (val result = provider.extractAcademic(
            AcademicExtractionRequest(messages, schemaVersion),
        )) {
            is AiProviderResult.Failure -> result
            is AiProviderResult.Success -> {
                if (result.result.schemaVersion != schemaVersion) {
                    AiProviderResult.Failure(
                        code = AiProviderResult.Failure.Code.INVALID_OUTPUT,
                        message = "Provider returned an unsupported schema version.",
                    )
                } else {
                    AcademicItemValidator.validateAll(
                        result.result.items,
                        schemaVersion,
                    ).fold(
                        onSuccess = { academicItems ->
                            ConversationInsightValidator.validateAll(
                                result.result.insights,
                                schemaVersion,
                            ).fold(
                                onSuccess = { insights ->
                                    AiProviderResult.Success(
                                        result.result.copy(
                                            items = academicItems,
                                            insights = insights,
                                        ),
                                    )
                                },
                                onFailure = {
                                    AiProviderResult.Failure(
                                        AiProviderResult.Failure.Code.INVALID_OUTPUT,
                                        it.message ?: "Provider returned invalid conversation insights.",
                                        it,
                                    )
                                },
                            )
                        },
                        onFailure = {
                            AiProviderResult.Failure(
                                code = AiProviderResult.Failure.Code.INVALID_OUTPUT,
                                message = it.message ?: "Provider returned invalid academic items.",
                                cause = it,
                            )
                        },
                    )
                }
            }
        }
    }
}
