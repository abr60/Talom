package com.talom.core.ai

/**
 * Safe factory boundary for future local and cloud implementations.
 *
 * Until each implementation has explicit privacy, credential, and schema
 * handling, unknown configurations fail closed instead of falling back.
 */
object ConfiguredAiProvider {
    fun create(config: AiProviderConfig, apiKey: String? = null): AiProvider =
        when (config.mode) {
            AiMode.DISABLED -> DisabledAiProvider(config)
            AiMode.CLOUD -> when (config.providerId) {
                "gemini" -> if (!apiKey.isNullOrBlank()) {
                    GeminiAiProvider(config, apiKey)
                } else {
                    UnavailableAiProvider(config)
                }
                "openai_compatible" -> if (!apiKey.isNullOrBlank() &&
                    !config.endpoint.isNullOrBlank() &&
                    !config.modelId.isNullOrBlank()
                ) {
                    OpenAiCompatibleAiProvider(config, apiKey)
                } else {
                    UnavailableAiProvider(config)
                }
                else -> UnavailableAiProvider(config)
            }
            AiMode.LOCAL,
            -> if (config.providerId == "ollama") {
                OllamaAiProvider(config)
            } else {
                UnavailableAiProvider(config)
            }
        }
}

private class UnavailableAiProvider(
    override val config: AiProviderConfig,
) : AiProvider {
    override suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult = AiProviderResult.Failure(
        code = AiProviderResult.Failure.Code.UNAVAILABLE,
        message = "The selected AI provider is not configured yet.",
    )
}
