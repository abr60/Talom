package com.talom.core.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Talks to any OpenAI-compatible Chat Completions API.
 *
 * Works with: OpenAI, OpenRouter, Groq, Mistral, Together, vLLM, llama.cpp server,
 * LM Studio, Ollama's `/v1` mode, and anything else that accepts the
 * `{endpoint}/chat/completions` and `{endpoint}/models` wire format.
 */
class OpenAiCompatibleAiProvider(
    override val config: AiProviderConfig,
    private val apiKey: String,
) : AiProvider {

    private val baseUrl: String
        get() = (config.endpoint ?: "").trim().trimEnd('/')

    override suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult {
        if (apiKey.isBlank()) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                "API key is required.",
            )
        }
        if (baseUrl.isBlank()) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                "Endpoint URL is required.",
            )
        }
        if (config.modelId.isNullOrBlank()) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                "Model is required. Pick one from the list or type a model name.",
            )
        }
        return withContext(Dispatchers.IO) {
            var last: AiProviderResult.Failure? = null
            repeat(3) { attempt ->
                val r = runCatching { requestWithModel(config.modelId!!, request) }
                    .getOrElse {
                        AiProviderResult.Failure(
                            AiProviderResult.Failure.Code.PROVIDER_ERROR,
                            it.message ?: "Request failed.",
                            it,
                        )
                    }
                if (r is AiProviderResult.Success) return@withContext r
                last = r as AiProviderResult.Failure
                val code = extractHttpCode(r.message)
                val retryable = code != null && AiRetry.isRetryableStatus(code)
                if (!retryable || attempt == 2) return@withContext r
                val retryAfter = Regex("""Retry-After:\s*([^\s\)]+)""").find(r.message)?.groupValues?.getOrNull(1)
                delay(AiRetry.backoffDelayMs(attempt, retryAfter))
            }
            last!!
        }
    }

    /** Lists model ids from `{endpoint}/models`. Empty list if not supported. */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("$baseUrl/models")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                if (connection.responseCode !in 200..299) return@runCatching emptyList()
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val data = json.optJSONArray("data") ?: return@runCatching emptyList()
                (0 until data.length()).mapNotNull { i ->
                    val obj = data.optJSONObject(i) ?: return@mapNotNull null
                    val id = obj.optString("id").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (isLikelyNonText(id)) null else id
                }.sorted()
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Cheap connection test used by the "Test connection" button. */
    suspend fun testConnection(): Result<ConnectionStatus> = withContext(Dispatchers.IO) {
        runCatching {
            // Fetch the model list (may fail, that's OK — we still try a chat completion).
            val models = listModels().getOrDefault(emptyList())
            val modelCount = models.size.takeIf { it > 0 }
            // Always verify auth with a real chat completion. /models on some providers
            // (e.g. OpenRouter) is public and doesn't require a key, so counting models
            // isn't a reliable auth check.
            val candidate = pickChatModelForTest(models)
            testViaChatCompletion(candidate, modelCount)
        }
    }

    /**
     * Pick a reasonable model to use for the connection test. Prefers the configured
     * model if it's set, else the first discovered model that looks chat-capable.
     */
    private fun pickChatModelForTest(models: List<String>): String? {
        val configured = config.modelId?.takeIf { it.isNotBlank() }
        if (configured != null) return configured
        if (models.isNotEmpty()) return models.first()
        return null
    }

    private fun testViaChatCompletion(model: String?, modelCount: Int?): ConnectionStatus {
        if (model.isNullOrBlank()) {
            return ConnectionStatus.Failed(
                CloudErrorMapper.CloudError(
                    headline = "No model available to test with.",
                    suggestion = "Type a model name in the Model field, or tap Refresh models.",
                    technical = "",
                )
            )
        }
        val connection = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode in 200..299) {
                ConnectionStatus.Ok(modelCount = modelCount, selectedModel = model)
            } else {
                ConnectionStatus.Failed(
                    CloudErrorMapper.mapHttp(
                        connection.responseCode,
                        response,
                        baseUrl,
                        model,
                    )
                )
            }
        } catch (t: Throwable) {
            ConnectionStatus.Failed(CloudErrorMapper.mapException(t, baseUrl))
        } finally {
            connection.disconnect()
        }
    }

    private fun requestWithModel(
        model: String,
        request: AcademicExtractionRequest,
    ): AiProviderResult {
        // Try with response_format=json_object first; retry without it if the endpoint rejects it.
        val attempts = listOf(true, false).distinct()
        for ((idx, useJsonMode) in attempts.withIndex()) {
            val body = buildBody(request, model, useJsonMode)
            val url = URL("$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    if (useJsonMode && isResponseFormatUnsupported(response) && idx == 0) continue
                    val err = CloudErrorMapper.mapHttp(code, response, baseUrl, model)
                    val retryAfter = connection.getHeaderField("Retry-After")
                    val suffix = retryAfter?.let { " (Retry-After: $it)" } ?: ""
                    return AiProviderResult.Failure(
                        AiProviderResult.Failure.Code.PROVIDER_ERROR,
                        "${err.headline}${err.suggestion?.let { " — $it" } ?: ""} (HTTP $code$suffix)",
                        IllegalStateException(err.technical + suffix),
                    )
                }
                val first = parseResponse(response, request.schemaVersion, model)
                if (first is AiProviderResult.Success) return first
                // Retry once on malformed JSON with an explicit nudge prompt.
                val failure = first as AiProviderResult.Failure
                if (failure.code == AiProviderResult.Failure.Code.INVALID_OUTPUT) {
                    val nudgeBody = buildBody(
                        request.copy(messages = request.messages),
                        model,
                        useJsonMode,
                    ).apply {
                        val msgs = getJSONArray("messages")
                        val original = msgs.getJSONObject(0).getString("content")
                        msgs.getJSONObject(0).put("content", buildRetryPrompt(original))
                    }
                    val retryConn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
                    try {
                        retryConn.requestMethod = "POST"
                        retryConn.connectTimeout = 15_000
                        retryConn.readTimeout = 60_000
                        retryConn.doOutput = true
                        retryConn.setRequestProperty("Content-Type", "application/json")
                        retryConn.setRequestProperty("Authorization", "Bearer $apiKey")
                        retryConn.outputStream.use { it.write(nudgeBody.toString().toByteArray()) }
                        val rc = retryConn.responseCode
                        val rs = if (rc in 200..299) retryConn.inputStream else retryConn.errorStream
                        val rt = rs?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (rc in 200..299) {
                            val second = parseResponse(rt, request.schemaVersion, model)
                            if (second is AiProviderResult.Success) return second
                        }
                    } finally {
                        retryConn.disconnect()
                    }
                }
                return first
            } finally {
                connection.disconnect()
            }
        }
        return AiProviderResult.Failure(
            AiProviderResult.Failure.Code.PROVIDER_ERROR,
            "Request failed.",
        )
    }

    private fun extractHttpCode(msg: String): Int? =
        Regex("""HTTP\s+(\d{3})""").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun buildBody(
        request: AcademicExtractionRequest,
        model: String,
        useJsonMode: Boolean = true,
    ): JSONObject {
        val prompt = AiPrompt.buildPrompt(request.messages, request.schemaVersion)
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
        if (useJsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        return body
    }

    private fun isResponseFormatUnsupported(response: String): Boolean =
        response.contains("response_format", ignoreCase = true) &&
            (response.contains("unsupported", ignoreCase = true) || response.contains("not supported", ignoreCase = true))

    private fun buildRetryPrompt(original: String): String =
        "$original\n\nYour previous reply was not valid JSON. Respond now with ONLY a single JSON object — no prose, no markdown fences, no explanation."

    private fun parseResponse(
        response: String,
        schemaVersion: Int,
        model: String,
    ): AiProviderResult {
        val json = try {
            JSONObject(response)
        } catch (e: Throwable) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_OUTPUT,
                CloudErrorMapper.mapInvalidJson(response, e.message ?: "JSON parse error").headline,
                e,
            )
        }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val text = message?.optString("content")
            ?: return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_OUTPUT,
                "Provider returned an empty response.",
            )
        // Some providers wrap JSON in ```json ... ``` fences. Strip them.
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val inner = try {
            JSONObject(cleaned)
        } catch (e: Throwable) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_OUTPUT,
                CloudErrorMapper.mapInvalidJson(text, e.message ?: "JSON parse error").headline,
                e,
            )
        }
        val items = (inner.optJSONArray("items") ?: JSONArray()).let { array ->
            (0 until array.length()).mapNotNull { index -> parseItem(array.getJSONObject(index), schemaVersion) }
        }
        return AiProviderResult.Success(
            AcademicExtractionResult(
                items = items,
                insights = parseInsights(inner, schemaVersion),
                providerId = config.providerId ?: "openai_compatible",
                modelId = model,
                schemaVersion = schemaVersion,
            ),
        )
    }

    private fun parseItem(json: JSONObject, schemaVersion: Int): AcademicItem? = try {
        val rawType = json.optString("type").trim().uppercase()
        val type = try { AcademicItemType.valueOf(rawType) } catch (_: IllegalArgumentException) { AcademicItemType.ANNOUNCEMENT }
        AcademicItem(
            stableId = json.optString("stableId"),
            type = type,
            title = json.optString("title"),
            details = json.optString("details").takeUnless { it == "null" || it.isEmpty() },
            subject = json.optString("subject").takeUnless { it == "null" || it.isEmpty() },
            dueAtMillis = if (json.isNull("dueAtMillis")) null else json.optLong("dueAtMillis"),
            sourceJid = json.optString("sourceJid"),
            sourceMessageId = json.optLong("sourceMessageId", 0L),
            confidence = json.optDouble("confidence", 0.7).toFloat(),
            extractionVersion = schemaVersion,
        )
    } catch (_: Exception) { null }

    private fun parseInsights(json: JSONObject, schemaVersion: Int): List<ConversationInsight> {
        val array = json.optJSONArray("insights") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            try {
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val rawType = item.optString("type").trim().uppercase()
                val type = try { InsightType.valueOf(rawType) } catch (_: IllegalArgumentException) { InsightType.GENERAL }
                ConversationInsight(
                    stableId = item.optString("stableId"),
                    type = type,
                    title = item.optString("title"),
                    details = item.optString("details").takeUnless { it == "null" || it.isEmpty() },
                    sourceJid = item.optString("sourceJid"),
                    sourceMessageId = item.optLong("sourceMessageId", 0L),
                    confidence = item.optDouble("confidence", 0.0).toFloat(),
                    extractionVersion = schemaVersion,
                )
            } catch (_: Exception) { null }
        }
    }

    private fun isLikelyNonText(id: String): Boolean {
        val lower = id.lowercase()
        return lower.contains("embedding") || lower.contains("embed") ||
            lower.contains("whisper") || lower.contains("tts") ||
            lower.contains("dall-e") || lower.contains("dalle") ||
            lower.contains("moderation") || lower.contains("search") ||
            lower.contains("similarity") || lower.contains("davinci-search") ||
            lower.contains("code-search") || lower.contains("babbage") ||
            lower.contains("audio") || lower.contains("realtime") ||
            lower.contains("image") || lower.contains("vision") && !lower.contains("chat")
    }
}

sealed interface ConnectionStatus {
    data class Ok(
        val modelCount: Int?,
        val selectedModel: String?,
    ) : ConnectionStatus

    data class Failed(
        val error: CloudErrorMapper.CloudError,
    ) : ConnectionStatus
}
