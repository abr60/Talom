package com.talom.ui.settings

import com.talom.core.ai.AiMode
import com.talom.core.ai.AiProviderConfig
import com.talom.data.source.PullLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Dynamic, data-driven strings — no literals that drift when provider/mode changes. */
object StatusText {

    fun aiConfigSummary(
        config: AiProviderConfig,
        hasKey: Boolean,
        cloudConsent: Boolean,
        localConsent: Boolean,
    ): String = when (config.mode) {
        AiMode.DISABLED -> "AI is disabled — no text leaves the phone."
        AiMode.LOCAL -> {
            val model = config.modelId?.takeIf { it.isNotBlank() } ?: "no model selected"
            val endpoint = config.endpoint?.takeIf { it.isNotBlank() } ?: "no endpoint"
            "Local Ollama · $model → $endpoint" +
                (if (!localConsent) " · consent OFF" else "")
        }
        AiMode.CLOUD -> {
            val model = config.modelId?.takeIf { it.isNotBlank() } ?: "no model selected"
            val key = if (hasKey) "key saved" else "no key"
            val consent = if (!cloudConsent) " · consent OFF" else ""
            when (config.providerId) {
                "openai_compatible" -> {
                    val endpoint = config.endpoint?.takeIf { it.isNotBlank() } ?: "no endpoint"
                    "Cloud · OpenAI-compatible · $model → $endpoint · $key$consent"
                }
                else -> "Cloud Gemini · $model · $key$consent"
            }
        }
    }

    fun aiSaveFeedback(
        config: AiProviderConfig,
        savedKeyNow: Boolean,
        cloudConsent: Boolean,
        localConsent: Boolean,
    ): String {
        val summary = aiConfigSummary(config, savedKeyNow, cloudConsent, localConsent)
        return "Saved — $summary"
    }

    fun aiMissingFields(
        mode: AiMode,
        useOpenAiCompatible: Boolean,
        endpointBlank: Boolean,
        keyBlank: Boolean,
        modelBlank: Boolean,
        cloudConsent: Boolean,
        localConsent: Boolean,
    ): String? {
        if (mode == AiMode.DISABLED) return null
        if (mode == AiMode.CLOUD && !cloudConsent) return "Enable consent before using Cloud AI."
        if (mode == AiMode.LOCAL && !localConsent) return "Enable local-network consent before using Ollama."
        if (mode == AiMode.CLOUD && useOpenAiCompatible) {
            val missing = buildList {
                if (endpointBlank) add("endpoint")
                if (keyBlank) add("API key")
                if (modelBlank) add("model")
            }
            if (missing.isNotEmpty()) return "Missing: ${missing.joinToString(", ")} — all are required for OpenAI-compatible."
        }
        return null
    }

    fun pullResult(
        extracted: Int,
        acad: Int,
        ins: Int,
        windowDays: Int,
        force: Boolean,
        providerLabel: String?,
        noNewMessagesCursorMillis: Long? = null,
    ): String = when {
        force && extracted == 0 -> "Re-extracted 0 messages over last ${windowDays}d — nothing in window to re-process."
        force -> "Re-extracted $extracted messages over last ${windowDays}d → $acad academic items, $ins insights" +
            (providerLabel?.let { " via $it" } ?: "")
        extracted == 0 -> {
            val hint = noNewMessagesCursorMillis?.let {
                val whenStr = try {
                    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                } catch (_: Throwable) { "last pull" }
                " (cursor at $whenStr, window ${windowDays}d)"
            } ?: ""
            "No new WhatsApp messages$hint — AI was not called."
        }
        else -> "Processed $extracted new messages → $acad academic items, $ins insights" +
            (providerLabel?.let { " via $it" } ?: "")
    }

    fun pullFailure(
        message: String,
        force: Boolean,
    ): String {
        val base = if (force) "Re-extract failed: $message" else "Pull failed: $message"
        return base
    }

    fun pullLogLine(log: PullLogEntity, formatTime: (Long) -> String): String {
        val ac = log.academicItemCount ?: 0
        val ins = log.insightCount ?: 0
        return "${formatTime(log.startedAtMillis)} ${log.state} ${log.itemCount} msgs, $ac acad, $ins pers"
    }

    fun hubAiCaption(mode: AiMode, providerId: String?): String = when (mode) {
        AiMode.CLOUD -> when (providerId) {
            "openai_compatible" -> "Cloud — OpenAI-compatible"
            else -> "Cloud Gemini"
        }
        AiMode.LOCAL -> "Local Ollama"
        AiMode.DISABLED -> "Off — no text leaves phone"
    }
}
