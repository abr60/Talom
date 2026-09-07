package com.talom.core.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the best model from a provider's model list for the Talom extraction task.
 *
 * Strategy: ask the same provider (using a cheap call) to choose. Falls back to a
 * local heuristic if the picker call fails.
 */
object ModelRecommender {

    private val PICK_PROMPT = """
        From the following list of model ids, pick the one best suited for structured JSON
        extraction of academic items (class schedules, assignments, exams, deadlines, announcements)
        and conversation insights (reminders, plans, family/friend mentions, general notes)
        from WhatsApp chat messages.

        Prefer small, fast, instruction-tuned models. JSON-mode / function-calling support is a plus.

        Reply with ONLY the chosen model id, nothing else. No explanation, no punctuation.
    """.trimIndent()

    suspend fun recommend(
        endpoint: String,
        apiKey: String,
        candidates: List<String>,
        candidateModel: String?,
    ): String? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        if (candidateModel == null || candidateModel !in candidates) {
            return fallbackHeuristic(candidates)
        }

        // Try asking the provider to pick.
        val pick = askProvider(endpoint, apiKey, candidateModel, candidates)
        if (pick != null && pick in candidates) return pick

        // Fallback to heuristic.
        return fallbackHeuristic(candidates)
    }

    private fun fallbackHeuristic(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        // Prefer names that mention instruct / chat / turbo / mini.
        val preferred = candidates.firstOrNull { id ->
            val lower = id.lowercase()
            lower.contains("instruct") || lower.contains("chat") ||
                lower.contains("turbo") || lower.contains("mini") ||
                lower.contains("haiku") || lower.contains("nano")
        }
        if (preferred != null) return preferred
        // Otherwise prefer free / lite / small variants.
        val lite = candidates.firstOrNull { id ->
            val lower = id.lowercase()
            (lower.contains("lite") || lower.contains("small") ||
                lower.contains("mini") || lower.contains("8b") ||
                lower.contains("haiku") || lower.contains("nano")) &&
                !lower.contains("embedding")
        }
        if (lite != null) return lite
        // Default: first alphabetically.
        return candidates.first()
    }

    private suspend fun askProvider(
        endpoint: String,
        apiKey: String,
        model: String,
        candidates: List<String>,
    ): String? {
        val result: String? = withContext(Dispatchers.IO) {
            runCatching<String?> {
                val url = URL("$endpoint/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 20_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    val body = JSONObject()
                        .put("model", model)
                        .put("max_tokens", 30)
                        .put("temperature", 0)
                        .put("messages", JSONArray().put(JSONObject()
                            .put("role", "user")
                            .put("content", "$PICK_PROMPT\n\nModels:\n" + candidates.joinToString("\n"))))
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val text = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")
                        ?: return@runCatching null
                    text.trim()
                        .removePrefix("`").removeSuffix("`")
                        .removePrefix("`")
                        .lines().firstOrNull()?.trim()
                        ?.takeIf { it.isNotEmpty() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        return result
    }
}
