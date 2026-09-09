package com.talom.core.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GeminiAiProvider(
    override val config: AiProviderConfig,
    private val apiKey: String,
) : AiProvider {
    override suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult {
        if (apiKey.isBlank() || config.modelId.isNullOrBlank()) {
            return AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                "Gemini API key and model are required.",
            )
        }
        return withContext(Dispatchers.IO) {
            val configured = config.modelId!!.trim().takeIf { it.isNotBlank() }
            // Only hit /models when no model is configured; otherwise use configured directly.
            // On 404 we lazily discover as fallback.
            var candidates: List<String> = if (configured != null) listOf(configured) else discoverModels()
            candidates = candidates.filter { it.isNotBlank() }
                .filter { name ->
                    val lower = name.lowercase()
                    !lower.contains("tts") && !lower.contains("image") &&
                        !lower.contains("imagen") && !lower.contains("veo") &&
                        !lower.contains("speech") && !lower.contains("audio") &&
                        !lower.contains("live") && !lower.contains("music") &&
                        !lower.contains("embedding") && !lower.contains("embed")
                }.distinct()
            if (candidates.isEmpty()) {
                return@withContext AiProviderResult.Failure(
                    AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                    "No Gemini model available.",
                )
            }
            var lastFailure: AiProviderResult.Failure? = null
            var triedDiscovery = configured == null
            var idx = 0
            while (idx < candidates.size) {
                val model = candidates[idx]
                when (val attempt = requestWithModelWithRetry(model, request)) {
                    is AiProviderResult.Success -> return@withContext attempt
                    is AiProviderResult.Failure -> {
                        lastFailure = attempt
                        val is404 = attempt.message.startsWith("Gemini HTTP 404")
                        if (is404 && !triedDiscovery) {
                            triedDiscovery = true
                            val discovered = discoverModels().filter { it != configured }
                                .filter { n ->
                                    val lower = n.lowercase()
                                    !lower.contains("tts") && !lower.contains("image") &&
                                        !lower.contains("imagen") && !lower.contains("veo") &&
                                        !lower.contains("speech") && !lower.contains("audio") &&
                                        !lower.contains("live") && !lower.contains("music") &&
                                        !lower.contains("embedding") && !lower.contains("embed")
                                }.distinct()
                            if (discovered.isNotEmpty()) {
                                candidates = candidates + discovered
                            } else {
                                return@withContext attempt
                            }
                        } else if (!is404) {
                            // 429/5xx already retried inside; non-retryable => fail fast
                            return@withContext attempt
                        }
                    }
                }
                idx++
            }
            lastFailure ?: AiProviderResult.Failure(
                AiProviderResult.Failure.Code.PROVIDER_ERROR,
                "Gemini request failed.",
            )
        }
    }

    /** Lists generateContent-capable model ids for this API key (for the model dropdown). */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = config.endpoint?.trimEnd('/')
                ?: "https://generativelanguage.googleapis.com"
            val connection = URL("$endpoint/v1beta/models?key=$apiKey")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                if (connection.responseCode !in 200..299) {
                    error("Gemini HTTP ${connection.responseCode}: invalid key or unavailable.")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val models = json.optJSONArray("models") ?: return@runCatching emptyList()
                (0 until models.length()).mapNotNull { index ->
                    val model = models.getJSONObject(index)
                    val methods = model.optJSONArray("supportedGenerationMethods")
                        ?: return@mapNotNull null
                    val supportsGenerate = (0 until methods.length()).any {
                        methods.optString(it) == "generateContent"
                    }
                    if (!supportsGenerate) return@mapNotNull null
                    val name = model.optString("name").removePrefix("models/")
                        .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val lower = name.lowercase()
                    if (lower.contains("tts") || lower.contains("image") ||
                        lower.contains("imagen") || lower.contains("veo") ||
                        lower.contains("speech") || lower.contains("audio") ||
                        lower.contains("live") || lower.contains("music") ||
                        lower.contains("embedding") || lower.contains("embed")
                    ) return@mapNotNull null
                    name
                }.sorted()
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun discoverModels(): List<String> = runCatching {
        val endpoint = config.endpoint?.trimEnd('/')
            ?: "https://generativelanguage.googleapis.com"
        val connection = URL("$endpoint/v1beta/models?key=$apiKey")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val models = json.optJSONArray("models") ?: return@runCatching emptyList()
            (0 until models.length()).mapNotNull { index ->
                val model = models.getJSONObject(index)
                val name = model.optString("name").removePrefix("models/")
                if (name.isBlank()) return@mapNotNull null
                val lower = name.lowercase()
                if (lower.contains("tts") || lower.contains("image") ||
                    lower.contains("imagen") || lower.contains("veo") ||
                    lower.contains("speech") || lower.contains("audio") ||
                    lower.contains("live") || lower.contains("music") ||
                    lower.contains("embedding") || lower.contains("embed") ||
                    lower.contains("vision") && lower.contains("preview")
                ) return@mapNotNull null
                val methods = model.optJSONArray("supportedGenerationMethods")
                    ?: return@mapNotNull null
                val supportsGenerate = (0 until methods.length()).any {
                    methods.optString(it) == "generateContent"
                }
                if (!supportsGenerate) return@mapNotNull null
                name
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())

    private suspend fun requestWithModelWithRetry(
        model: String,
        request: AcademicExtractionRequest,
    ): AiProviderResult {
        var last: AiProviderResult.Failure? = null
        repeat(3) { attempt ->
            when (val r = requestWithModel(model, request)) {
                is AiProviderResult.Success -> return r
                is AiProviderResult.Failure -> {
                    last = r
                    val code = r.message.substringAfter("Gemini HTTP ").substringBefore(":").substringBefore(" ").toIntOrNull()
                    val retryable = code != null && AiRetry.isRetryableStatus(code)
                    if (!retryable || attempt == 2) return r
                    // Extract Retry-After if present in message (provider may include it in body, but header is authoritative;
                    // requestWithModel doesn't expose header, so use exponential backoff here)
                    delay(AiRetry.backoffDelayMs(attempt, null))
                }
            }
        }
        return last!!
    }

    private fun requestWithModel(
        model: String,
        request: AcademicExtractionRequest,
    ): AiProviderResult = runCatching {
        val endpoint = config.endpoint?.trimEnd('/')
            ?: "https://generativelanguage.googleapis.com"
        val url = URL("$endpoint/v1beta/models/$model:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(buildBody(request).toString().toByteArray()) }
            val code = connection.responseCode
            val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val retryAfter = connection.getHeaderField("Retry-After")
                val suffix = retryAfter?.let { " (Retry-After: $it)" } ?: ""
                error("Gemini HTTP $code: ${response.take(300)}$suffix")
            }
            parseResponse(response, request.schemaVersion, model)
        } finally {
            connection.disconnect()
        }
    }.getOrElse {
        AiProviderResult.Failure(
            AiProviderResult.Failure.Code.PROVIDER_ERROR,
            it.message ?: "Gemini request failed.",
            it,
        )
    }

    private fun buildBody(request: AcademicExtractionRequest): JSONObject {
        val prompt = AiPrompt.buildPrompt(request.messages, request.schemaVersion)
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
    }

    private fun parseResponse(
        response: String,
        schemaVersion: Int,
        model: String,
    ): AiProviderResult {
        val text = JSONObject(response)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        val json = JSONObject(text)
        val items = (json.optJSONArray("items") ?: JSONArray()).let { array ->
            (0 until array.length()).mapNotNull { index -> parseItem(array.getJSONObject(index), schemaVersion) }
        }
        return AiProviderResult.Success(
            AcademicExtractionResult(
                items = items,
                insights = parseInsights(json, schemaVersion),
                providerId = config.providerId ?: "gemini",
                modelId = model,
                schemaVersion = schemaVersion,
            ),
        )
    }

    private fun parseItem(json: JSONObject, schemaVersion: Int): AcademicItem? = try {
        val rawType = json.getString("type").trim().uppercase()
        val type = try { AcademicItemType.valueOf(rawType) } catch (_: IllegalArgumentException) { AcademicItemType.ANNOUNCEMENT }
        AcademicItem(
            stableId = json.getString("stableId"),
            type = type,
            title = json.getString("title"),
            details = json.optString("details").takeUnless { it == "null" },
            subject = json.optString("subject").takeUnless { it == "null" },
            dueAtMillis = if (json.isNull("dueAtMillis")) null else json.optLong("dueAtMillis"),
            sourceJid = json.getString("sourceJid"),
            sourceMessageId = json.getLong("sourceMessageId"),
            confidence = json.optDouble("confidence", 0.7).toFloat(),
            extractionVersion = schemaVersion,
        )
    } catch (_: Exception) { null }

    private fun parseInsights(json: JSONObject, schemaVersion: Int): List<ConversationInsight> {
        val array = json.optJSONArray("insights") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            try {
                val item = array.getJSONObject(index)
                val rawType = item.getString("type").trim().uppercase()
                val type = try { InsightType.valueOf(rawType) } catch (_: IllegalArgumentException) { InsightType.GENERAL }
                ConversationInsight(
                    stableId = item.getString("stableId"),
                    type = type,
                    title = item.getString("title"),
                    details = item.optString("details").takeUnless { it == "null" },
                    sourceJid = item.getString("sourceJid"),
                    sourceMessageId = item.getLong("sourceMessageId"),
                    confidence = item.optDouble("confidence", 0.7).toFloat(),
                    extractionVersion = schemaVersion,
                )
            } catch (_: Exception) { null }
        }
    }
}
