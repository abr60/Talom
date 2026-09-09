package com.talom.core.ai

import android.util.Log
import com.talom.core.source.SourceMessage

class AcademicExtractionService(
    val provider: AiProvider,
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

        val bengaliMsgs = messages.count { m ->
            m.text.orEmpty().any { it in '\u0980'..'\u09FF' }
        }
        Log.i(TAG, "Extracting ${messages.size} messages ($bengaliMsgs Bengali) via ${provider.config.providerId}/${provider.config.modelId}")

        return when (val result = provider.extractAcademic(
            AcademicExtractionRequest(messages, schemaVersion),
        )) {
            is AiProviderResult.Failure -> {
                Log.e(TAG, "Extraction failed: ${result.message}", result.cause)
                result
            }
            is AiProviderResult.Success -> {
                if (result.result.schemaVersion != schemaVersion) {
                    Log.e(TAG, "Unsupported schema ${result.result.schemaVersion}")
                    AiProviderResult.Failure(
                        code = AiProviderResult.Failure.Code.INVALID_OUTPUT,
                        message = "Provider returned an unsupported schema version.",
                    )
                } else {
                    val itemValidation = AcademicItemValidator.validateAll(
                        result.result.items,
                        schemaVersion,
                    )
                    val insightValidation = ConversationInsightValidator.validateAll(
                        result.result.insights,
                        schemaVersion,
                    )
                    val hardItemDrops = itemValidation.errors.count { it.startsWith("drop:") }
                    val hardInsightDrops = insightValidation.errors.count { it.startsWith("drop") }
                    if (itemValidation.errors.isNotEmpty() || insightValidation.errors.isNotEmpty()) {
                        Log.w(
                            TAG,
                            "Validation: kept ${itemValidation.valid.size}/${result.result.items.size} items, " +
                                "${insightValidation.valid.size}/${result.result.insights.size} insights; " +
                                "errors=${(itemValidation.errors + insightValidation.errors).take(8)}",
                        )
                    } else {
                        Log.i(
                            TAG,
                            "Extracted ${itemValidation.valid.size} academic / ${insightValidation.valid.size} insights",
                        )
                    }
                    AiProviderResult.Success(
                        result.result.copy(
                            items = itemValidation.valid,
                            insights = insightValidation.valid,
                            validationErrors = itemValidation.errors + insightValidation.errors,
                            droppedItemCount = hardItemDrops,
                            droppedInsightCount = hardInsightDrops,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TalomExtract"
    }
}
