package com.talom.core.ai

import com.talom.core.source.SourceMessage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
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
            val models = (listOf(config.modelId) + discoverModels())
                .filterNotNull()
                .filter { it.isNotBlank() }
                .filter { name ->
                    val lower = name.lowercase()
                    !lower.contains("tts") && !lower.contains("image") &&
                        !lower.contains("imagen") && !lower.contains("veo") &&
                        !lower.contains("speech") && !lower.contains("audio") &&
                        !lower.contains("live") && !lower.contains("music") &&
                        !lower.contains("embedding") && !lower.contains("embed")
                }
                .distinct()
            var lastFailure: AiProviderResult.Failure? = null
            for (model in models) {
                when (val attempt = requestWithModel(model, request)) {
                    is AiProviderResult.Success -> return@withContext attempt
                    is AiProviderResult.Failure -> {
                        lastFailure = attempt
                        if (!attempt.message.startsWith("Gemini HTTP 404") &&
                            !attempt.message.startsWith("Gemini HTTP 503")
                        ) {
                            return@withContext attempt
                        }
                    }
                }
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
                    // Exclude non-text models. The Gemini list endpoint reports
                    // supportedGenerationMethods=generateContent for TTS/image models too,
                    // but their response modalities are AUDIO/IMAGE only — they 400 on TEXT.
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
                // Exclude non-text models. The Gemini list endpoint reports
                // supportedGenerationMethods=generateContent for TTS/image models too,
                // but their response modalities are AUDIO/IMAGE only — they 400 on TEXT.
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
            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("Gemini HTTP ${connection.responseCode}: ${response.take(300)}")
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
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
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
            (0 until array.length()).map { index -> parseItem(array.getJSONObject(index), schemaVersion) }
        }
        val validated = AcademicItemValidator.validateAll(items, schemaVersion)
        return validated.fold(
            onSuccess = {
                AiProviderResult.Success(
                    AcademicExtractionResult(
                        items = it,
                                insights = parseInsights(json, schemaVersion),
                        providerId = config.providerId ?: "gemini",
                        modelId = model,
                        schemaVersion = schemaVersion,
                    ),
                )
            },
            onFailure = {
                AiProviderResult.Failure(
                    AiProviderResult.Failure.Code.INVALID_OUTPUT,
                    it.message ?: "Gemini returned invalid academic items.",
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
