package com.talom.core.ai

/**
 * Maps cloud provider errors to user-facing messages with actionable suggestions.
 * Pure functions — easy to test, no Android dependencies.
 */
object CloudErrorMapper {

    data class CloudError(
        val headline: String,
        val suggestion: String?,
        val technical: String,
    )

    /**
     * Map an HTTP status code + raw error body to a [CloudError].
     * Status 0 indicates a non-HTTP failure (connection/timeout/etc).
     */
    fun mapHttp(
        status: Int,
        body: String?,
        endpoint: String? = null,
        model: String? = null,
    ): CloudError {
        val tech = if (body.isNullOrBlank()) "HTTP $status" else "HTTP $status: $body"
        return when (status) {
            0 -> CloudError(
                headline = "Can't reach the endpoint.",
                suggestion = "Check the URL and that the service is running." +
                    (endpoint?.let { " Tried: $it" } ?: ""),
                technical = tech,
            )
            401 -> CloudError(
                headline = "API key is invalid or expired.",
                suggestion = "Check your key at the provider's dashboard, then paste a new one and Save.",
                technical = tech,
            )
            403 -> CloudError(
                headline = "API key doesn't have access.",
                suggestion = "Your key may be missing scopes or your plan doesn't cover this model. " +
                    "Try a different model from the list.",
                technical = tech,
            )
            404 -> CloudError(
                headline = (model?.let { "Model '$it' not found." }
                    ?: "Endpoint or model not found."),
                suggestion = "Pick a different model from the dropdown, or check the endpoint URL.",
                technical = tech,
            )
            408 -> CloudError(
                headline = "Request timed out.",
                suggestion = "The provider may be slow or unreachable. Try again in a moment.",
                technical = tech,
            )
            413 -> CloudError(
                headline = "Request too large.",
                suggestion = "Reduce the message window in AI settings (e.g. 7 days) and try again.",
                technical = tech,
            )
            429 -> CloudError(
                headline = "Rate limit hit.",
                suggestion = "Wait a minute, or switch to a different free provider. " +
                    "OpenRouter and Groq have generous free tiers — paste their endpoint and key in AI settings.",
                technical = tech,
            )
            500, 502, 503, 504 -> CloudError(
                headline = "Provider is having issues.",
                suggestion = "The service is temporarily unavailable. Try again in a few minutes.",
                technical = tech,
            )
            else -> CloudError(
                headline = "Provider error (HTTP $status).",
                suggestion = "If this keeps happening, try a different model or provider.",
                technical = tech,
            )
        }
    }

    /**
     * Map a low-level exception (before any HTTP response) to a [CloudError].
     */
    fun mapException(throwable: Throwable, endpoint: String? = null): CloudError {
        val msg = throwable.message ?: throwable.javaClass.simpleName
        return when {
            msg.contains("timeout", ignoreCase = true) -> CloudError(
                headline = "Connection timed out.",
                suggestion = "The provider is slow or unreachable. Try again or check the URL.",
                technical = msg,
            )
            msg.contains("refused", ignoreCase = true) -> CloudError(
                headline = "Connection refused.",
                suggestion = "The service isn't accepting connections. Check the URL and that the service is running." +
                    (endpoint?.let { " Tried: $it" } ?: ""),
                technical = msg,
            )
            msg.contains("UnknownHost", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) -> CloudError(
                headline = "Can't reach the host.",
                suggestion = "Check the URL — it may be misspelled or the service may be down.",
                technical = msg,
            )
            msg.contains("SSL", ignoreCase = true) ||
                msg.contains("Certificate", ignoreCase = true) ||
                msg.contains("Trust", ignoreCase = true) -> CloudError(
                headline = "TLS / certificate error.",
                suggestion = "Check the URL scheme (https://) and that the certificate is valid.",
                technical = msg,
            )
            else -> CloudError(
                headline = "Network error.",
                suggestion = "Check the URL and your network connection.",
                technical = msg,
            )
        }
    }

    /**
     * Detect "the model replied but the body isn't valid JSON" (common when a model
     * is bad at structured output). Caller passes the raw reply text and the parse error.
     */
    fun mapInvalidJson(reply: String?, parseError: String): CloudError = CloudError(
        headline = "Model returned malformed output.",
        suggestion = "Some models are bad at JSON. Try a different model from the dropdown — " +
            "instruct-tuned or code-tuned models tend to be more reliable.",
        technical = "Parse error: $parseError" + (reply?.let { "\nReply: ${it.take(300)}" } ?: ""),
    )
}
