package com.talom.core.ai

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
    private val keyAlias = "talom_ai_api_key_v2"

    companion object {
        const val KEY_OLLAMA_ENDPOINT = "ollama_endpoint"
        const val KEY_OPENAI_ENDPOINT = "openai_endpoint"
        const val DEFAULT_OPENAI_ENDPOINT = "https://openrouter.ai/api/v1"
    }

    fun ollamaEndpoint(): String {
        preferences.getString(KEY_OLLAMA_ENDPOINT, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val legacy = preferences.getString("endpoint", null)
        val providerId = preferences.getString("provider_id", null)
        if (!legacy.isNullOrBlank()) {
            val looksLikeOpenAi = legacy.contains("openrouter") || legacy.trim().startsWith("https://")
            // If legacy was saved while on openai_compatible, don't reuse it for Ollama.
            if (providerId == "openai_compatible" && looksLikeOpenAi) {
                return com.talom.core.TalomPreferences.DEFAULT_OLLAMA_ENDPOINT
            }
            if (!looksLikeOpenAi) return legacy
        }
        return com.talom.core.TalomPreferences.DEFAULT_OLLAMA_ENDPOINT
    }

    fun openAiEndpoint(): String {
        preferences.getString(KEY_OPENAI_ENDPOINT, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val legacy = preferences.getString("endpoint", null)
        if (!legacy.isNullOrBlank()) {
            if (legacy.contains("localhost") || legacy.contains("127.0.0.1")) {
                return DEFAULT_OPENAI_ENDPOINT
            }
            if (legacy.contains("openrouter") || legacy.trim().startsWith("https://")) return legacy
            // Legacy looks like an Ollama LAN URL — not valid for OpenAI.
            return DEFAULT_OPENAI_ENDPOINT
        }
        return DEFAULT_OPENAI_ENDPOINT
    }

    fun config(): AiProviderConfig {
        val mode = runCatching { AiMode.valueOf(preferences.getString("mode", AiMode.LOCAL.name)!!) }
            .getOrDefault(AiMode.LOCAL)
        val providerId = preferences.getString("provider_id", if (mode == AiMode.LOCAL) "ollama" else null)
        val endpoint = when (providerId) {
            "openai_compatible" -> openAiEndpoint()
            "ollama" -> ollamaEndpoint()
            else -> {
                // Gemini / disabled have no endpoint; keep per-provider endpoints preserved.
                if (mode == AiMode.LOCAL) ollamaEndpoint() else null
            }
        }
        val rawModel = preferences.getString("model_id", null)
            ?.replace("gemini-2.5-flash", "gemini-3.6-flash")
        // Ollama ids like "llama3.1:8b" are not valid on OpenRouter — swap to a
        // free OpenRouter default so pull still works; user can change in UI.
        val modelId = when {
            providerId == "openai_compatible" &&
                rawModel != null &&
                rawModel.contains(":") &&
                !rawModel.contains("/") -> "google/gemma-3-4b-it:free"
            else -> rawModel
        }
        return AiProviderConfig(
            mode = mode,
            providerId = providerId,
            modelId = modelId,
            endpoint = endpoint,
        )
    }

    fun setConfig(config: AiProviderConfig) {
        val editor = preferences.edit()
            .putString("mode", config.mode.name)
            .putString("provider_id", config.providerId)
            .putString("model_id", config.modelId)
        when (config.providerId) {
            "openai_compatible" -> {
                val ep = config.endpoint?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_OPENAI_ENDPOINT
                editor.putString(KEY_OPENAI_ENDPOINT, ep)
                editor.putString("endpoint", ep)
            }
            "ollama" -> {
                val ep = config.endpoint?.trim()?.takeIf { it.isNotBlank() }
                    ?: com.talom.core.TalomPreferences.DEFAULT_OLLAMA_ENDPOINT
                editor.putString(KEY_OLLAMA_ENDPOINT, ep)
                editor.putString("endpoint", ep)
            }
            else -> {
                // Gemini / disabled: preserve per-provider endpoints, only update legacy.
                if (config.endpoint != null) editor.putString("endpoint", config.endpoint)
                else editor.remove("endpoint")
            }
        }
        editor.apply()
    }

    fun apiKey(): String? {
        val encoded = preferences.getString("api_key", null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            }.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun setApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            preferences.edit().remove("api_key").apply()
            return
        }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        }
        preferences.edit()
            .putString("api_key", Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun cloudConsent(): Boolean = preferences.getBoolean("cloud_consent", false)

    fun setCloudConsent(consented: Boolean) {
        preferences.edit().putBoolean("cloud_consent", consented).apply()
    }

    fun localConsent(): Boolean = preferences.getBoolean("local_consent", false)

    fun setLocalConsent(consented: Boolean) {
        preferences.edit().putBoolean("local_consent", consented).apply()
    }

    fun messageWindowDays(): Int = preferences.getInt("message_window_days", 30)

    fun setMessageWindowDays(days: Int) {
        preferences.edit().putInt("message_window_days", days).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }
}
