package com.talom.core.ai

import com.talom.core.source.SourceMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OllamaModelInfo(
    val name: String,
    val hasTools: Boolean,
    val paramSizeBillions: Double,
    val contextLength: Int,
)

object OllamaModelRanker {
    fun parseParamSizeBillions(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val s = raw.trim()
        return when {
            s.endsWith("B", ignoreCase = true) -> s.dropLast(1).toDoubleOrNull() ?: 0.0
            s.endsWith("M", ignoreCase = true) -> (s.dropLast(1).toDoubleOrNull() ?: 0.0) / 1_000.0
            s.endsWith("K", ignoreCase = true) -> (s.dropLast(1).toDoubleOrNull() ?: 0.0) / 1_000_000.0
            else -> s.toDoubleOrNull() ?: 0.0
        }
    }

    fun pickBest(models: List<OllamaModelInfo>): OllamaModelInfo? {
        if (models.isEmpty()) return null
        return models.sortedWith(
            compareByDescending<OllamaModelInfo> { if (it.hasTools) 1 else 0 }
                .thenByDescending { it.paramSizeBillions }
                .thenByDescending { it.contextLength }
                .thenBy { it.name }
        ).firstOrNull()
    }
}

class OllamaAiProvider(
    override val config: AiProviderConfig,
) : AiProvider {
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = config.endpoint?.trim()?.trimEnd('/') ?: error("Ollama endpoint is required.")
            val connection = URL("$endpoint/api/tags").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                check(connection.responseCode in 200..299) { "Ollama HTTP ${connection.responseCode}" }
                val models = JSONObject(
                    connection.inputStream.bufferedReader().use { it.readText() },
                ).optJSONArray("models") ?: JSONArray()
                (0 until models.length()).mapNotNull {
                    models.getJSONObject(it).optString("name").takeIf(String::isNotBlank)
                }.sorted()
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun listModelsDetailed(): Result<List<OllamaModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = config.endpoint?.trim()?.trimEnd('/') ?: error("Ollama endpoint is required.")
            val connection = URL("$endpoint/api/tags").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                check(connection.responseCode in 200..299) { "Ollama HTTP ${connection.responseCode}" }
                val models = JSONObject(
                    connection.inputStream.bufferedReader().use { it.readText() },
                ).optJSONArray("models") ?: JSONArray()
                (0 until models.length()).mapNotNull { idx ->
                    val obj = models.getJSONObject(idx)
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val capabilities = obj.optJSONArray("capabilities")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                    } ?: emptyList()
                    val hasTools = "tools" in capabilities
                    val details = obj.optJSONObject("details")
                    val paramRaw = details?.optString("parameter_size")
                    val paramB = OllamaModelRanker.parseParamSizeBillions(paramRaw)
                    val ctx = details?.optInt("context_length", 0) ?: 0
                    // Fallback: some Ollama variants put context in top-level
                    val ctx2 = if (ctx != 0) ctx else obj.optInt("context_length", 0)
                    OllamaModelInfo(name, hasTools, paramB, ctx2)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun testConnection(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = config.endpoint?.trim()?.trimEnd()
                ?: error("Ollama endpoint is required.")
            val connection = URL("$endpoint/api/tags").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                check(connection.responseCode in 200..299) {
                    "Ollama HTTP ${connection.responseCode}"
                }
                val models = JSONObject(
                    connection.inputStream.bufferedReader().use { it.readText() },
                ).optJSONArray("models") ?: JSONArray()
                (0 until models.length()).mapNotNull {
                    models.getJSONObject(it).optString("name").takeIf(String::isNotBlank)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun extractAcademic(
        request: AcademicExtractionRequest,
    ): AiProviderResult = withContext(Dispatchers.IO) {
        val endpoint = config.endpoint?.trim()?.trimEnd('/')
        val model = config.modelId?.trim()
        if (endpoint.isNullOrBlank() || model.isNullOrBlank()) {
            return@withContext AiProviderResult.Failure(
                AiProviderResult.Failure.Code.INVALID_CONFIGURATION,
                "Ollama endpoint and model are required.",
            )
        }
        runCatching {
            val connection = URL("$endpoint/api/generate").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 300_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(buildBody(request, model).toString().toByteArray(Charsets.UTF_8))
                }
                val response = (if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) {
                    error("Ollama HTTP ${connection.responseCode}: ${response.take(300)}")
                }
                parseResponse(response, request.schemaVersion, model)
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            AiProviderResult.Failure(
                AiProviderResult.Failure.Code.PROVIDER_ERROR,
                it.message ?: "Ollama request failed.",
                it,
            )
        }
    }

    private fun buildBody(request: AcademicExtractionRequest, model: String): JSONObject {
        val prompt = AiPrompt.buildPrompt(
            request.messages,
            request.schemaVersion,
            useExamples = false,
        )
        return JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("stream", false)
            .put("format", "json")
            .put("think", false)
            .put(
                "options",
                JSONObject()
                    .put("temperature", 0)
                    .put("num_predict", 1536)
                    .put("num_ctx", 8192),
            )
    }

    private fun parseResponse(
        response: String,
        schemaVersion: Int,
        model: String,
    ): AiProviderResult {
        val text = JSONObject(response).getString("response")
        val json = JSONObject(text)
        val items = (json.optJSONArray("items") ?: JSONArray()).let { array ->
            (0 until array.length()).mapNotNull { parseItem(array.getJSONObject(it), schemaVersion) }
        }
        return AiProviderResult.Success(
            AcademicExtractionResult(
                items = items,
                insights = parseInsights(json, schemaVersion),
                providerId = config.providerId ?: "ollama",
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
