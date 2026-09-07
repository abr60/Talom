package com.talom.core.ai

import com.talom.core.source.SourceMessage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
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
            runCatching { requestWithModel(config.modelId, request) }
                .getOrElse {
                    AiProviderResult.Failure(
                        AiProviderResult.Failure.Code.PROVIDER_ERROR,
                        it.message ?: "Request failed.",
                        it,
                    )
                }
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
        val url = URL("$baseUrl/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { it.write(buildBody(request, model).toString().toByteArray()) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val err = CloudErrorMapper.mapHttp(
                    connection.responseCode,
                    response,
                    baseUrl,
                    model,
                )
                return AiProviderResult.Failure(
                    AiProviderResult.Failure.Code.PROVIDER_ERROR,
                    "${err.headline}${err.suggestion?.let { " — $it" } ?: ""}",
                    IllegalStateException(err.technical),
                )
            }
            parseResponse(response, request.schemaVersion, model)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildBody(request: AcademicExtractionRequest, model: String): JSONObject {
        val messages = request.messages.joinToString("\n") {
            "[${it.timestampMillis}] ${it.jid}: ${it.text.orEmpty()}"
        }
        val prompt = """
            Extract academic items and useful general conversation insights from the messages below.
            Preserve the original language of titles and details. Do not translate.

            For each item, dueAtMillis MUST be when the EVENT OCCURS, not when the message was sent.
            - CLASS_SCHEDULE: next occurrence of the class day + start time. If the message says
              "tomorrow 9 AM" sent on Aug 30, dueAtMillis is Aug 31 09:00. If only a day-of-week
              is given ("Sunday 9 AM"), use the next occurrence of that day. For weekly recurring
              schedules, pick the next upcoming instance of that weekday.
            - ASSIGNMENT / DEADLINE: the actual submission deadline parsed from the text.
            - EXAM / CLASS_TEST / VIVA / PRACTICAL / INTERVIEW / PRESENTATION: the event date+time.
            - ANNOUNCEMENT / CANCELLATION: null (no event time).
            - If truly ambiguous, set dueAtMillis to null. Do not invent facts.

            Type vocabulary (use the closest match):
            CLASS_SCHEDULE | ASSIGNMENT | EXAM | DEADLINE | ANNOUNCEMENT | CANCELLATION |
            CLASS_TEST | PRESENTATION | VIVA | INTERVIEW | PRACTICAL

            Return ONLY valid JSON matching:
            {"items":[{"stableId":"string","type":"one of the above","title":"string","details":"string|null","subject":"string|null","dueAtMillis":0,"sourceJid":"string","sourceMessageId":0,"confidence":0.0,"extractionVersion":${request.schemaVersion}}],"insights":[{"stableId":"string","type":"PERSONAL_REMINDER|PLAN|FAMILY|FRIEND|GENERAL","title":"string","details":"string|null","sourceJid":"string","sourceMessageId":0,"confidence":0.0,"extractionVersion":${request.schemaVersion}}]}
            Put non-academic useful information in insights. Return empty arrays when nothing is useful.
            Messages:
            $messages
        """.trimIndent()
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("content", prompt)))
            .put("temperature", 0)
    }

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
            (0 until array.length()).map { index -> parseItem(array.getJSONObject(index), schemaVersion) }
        }
        val validated = AcademicItemValidator.validateAll(items, schemaVersion)
        return validated.fold(
            onSuccess = {
                AiProviderResult.Success(
                    AcademicExtractionResult(
                        items = it,
                        insights = parseInsights(inner, schemaVersion),
                        providerId = config.providerId ?: "openai_compatible",
                        modelId = model,
                        schemaVersion = schemaVersion,
                    ),
                )
            },
            onFailure = {
                AiProviderResult.Failure(
                    AiProviderResult.Failure.Code.INVALID_OUTPUT,
                    it.message ?: "Provider returned invalid academic items.",
                    it,
                )
            },
        )
    }

    private fun parseItem(json: JSONObject, schemaVersion: Int): AcademicItem =
        AcademicItem(
            stableId = json.getString("stableId"),
            type = AcademicItemType.valueOf(json.getString("type")),
            title = json.getString("title"),
            details = json.optString("details").takeUnless { it == "null" || it.isEmpty() },
            subject = json.optString("subject").takeUnless { it == "null" || it.isEmpty() },
            dueAtMillis = if (json.isNull("dueAtMillis")) null else json.optLong("dueAtMillis"),
            sourceJid = json.getString("sourceJid"),
            sourceMessageId = json.getLong("sourceMessageId"),
            confidence = json.getDouble("confidence").toFloat(),
            extractionVersion = schemaVersion,
        )

    private fun parseInsights(json: JSONObject, schemaVersion: Int): List<ConversationInsight> {
        val array = json.optJSONArray("insights") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = runCatching { InsightType.valueOf(item.optString("type")) }.getOrNull()
                ?: return@mapNotNull null
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
