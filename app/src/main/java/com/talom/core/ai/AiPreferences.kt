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

    fun config(): AiProviderConfig = AiProviderConfig(
        mode = runCatching { AiMode.valueOf(preferences.getString("mode", AiMode.DISABLED.name)!!) }
            .getOrDefault(AiMode.DISABLED),
        providerId = preferences.getString("provider_id", null),
        modelId = preferences.getString("model_id", null)
            ?.replace("gemini-2.5-flash", "gemini-3.6-flash"),
        endpoint = preferences.getString("endpoint", null),
    )

    fun setConfig(config: AiProviderConfig) {
        preferences.edit()
            .putString("mode", config.mode.name)
            .putString("provider_id", config.providerId)
            .putString("model_id", config.modelId)
            .putString("endpoint", config.endpoint)
            .apply()
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
