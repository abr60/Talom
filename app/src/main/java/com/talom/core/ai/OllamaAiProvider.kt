package com.talom.core.ai

import com.talom.core.source.SourceMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val messages = request.messages.joinToString("\n") {
            "[${it.timestampMillis}] ${it.jid}: ${it.text.orEmpty()}"
        }
        val prompt = """
            Extract academic items and useful general conversation insights from the messages below.
            Return ONLY valid JSON matching:
            {"items":[{"stableId":"string","type":"CLASS_SCHEDULE|ASSIGNMENT|EXAM|DEADLINE|ANNOUNCEMENT|CANCELLATION","title":"string","details":"string|null","subject":"string|null","dueAtMillis":0,"sourceJid":"string","sourceMessageId":0,"confidence":0.0,"extractionVersion":${request.schemaVersion}}],"insights":[{"stableId":"string","type":"PERSONAL_REMINDER|PLAN|FAMILY|FRIEND|GENERAL","title":"string","details":"string|null","sourceJid":"string","sourceMessageId":0,"confidence":0.0,"extractionVersion":${request.schemaVersion}}]}
            Use null for unknown dueAtMillis. Do not invent facts.
            Put non-academic useful information in insights. Return empty arrays when nothing is useful.
            Messages:
            $messages
        """.trimIndent()
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
                    .put("num_predict", 768)
                    .put("num_ctx", 4096),
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
            (0 until array.length()).map { parseItem(array.getJSONObject(it), schemaVersion) }
        }
        val validated = AcademicItemValidator.validateAll(items, schemaVersion)
        return validated.fold(
            onSuccess = {
                AiProviderResult.Success(
                    AcademicExtractionResult(
                        items = it,
                        insights = parseInsights(json, schemaVersion),
                        providerId = config.providerId ?: "ollama",
                        modelId = model,
                        schemaVersion = schemaVersion,
                    ),
                )
            },
            onFailure = {
                AiProviderResult.Failure(
                    AiProviderResult.Failure.Code.INVALID_OUTPUT,
                    it.message ?: "Ollama returned invalid academic items.",
                    it,
                )
            },
        )
    }

    private fun parseItem(json: JSONObject, schemaVersion: Int) = AcademicItem(
        stableId = json.getString("stableId"),
        type = AcademicItemType.valueOf(json.getString("type")),
        title = json.getString("title"),
        details = json.optString("details").takeUnless { it == "null" },
        subject = json.optString("subject").takeUnless { it == "null" },
        dueAtMillis = if (json.isNull("dueAtMillis")) null else json.optLong("dueAtMillis"),
        sourceJid = json.getString("sourceJid"),
        sourceMessageId = json.getLong("sourceMessageId"),
        confidence = json.getDouble("confidence").toFloat(),
        extractionVersion = schemaVersion,
    )

    private fun parseInsights(json: JSONObject, schemaVersion: Int): List<ConversationInsight> {
        val array = json.optJSONArray("insights") ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ConversationInsight(
                stableId = item.getString("stableId"),
                type = InsightType.valueOf(item.getString("type")),
                title = item.getString("title"),
                details = item.optString("details").takeUnless { it == "null" },
                sourceJid = item.getString("sourceJid"),
                sourceMessageId = item.getLong("sourceMessageId"),
                confidence = item.getDouble("confidence").toFloat(),
                extractionVersion = schemaVersion,
            )
        }
    }
}
