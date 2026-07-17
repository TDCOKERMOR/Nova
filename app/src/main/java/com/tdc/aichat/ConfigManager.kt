package com.tdc.aichat

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ai_chat_config_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Cached config to avoid repeated reads. Invalidate on save. */
    @Volatile private var cached: AppConfig? = null

    fun loadConfig(): AppConfig {
        cached?.let { return it }
        val config = AppConfig(
            chatApiUrl = prefs.getString("chat_api_url", "") ?: "",
            chatApiKey = prefs.getString("chat_api_key", "") ?: "",
            chatModel = prefs.getString("chat_model", "") ?: "",
            temperature = prefs.getFloat("temperature", 0.7f),
            topP = prefs.getFloat("top_p", 1.0f),
            maxTokens = prefs.getInt("max_tokens", 2048),
            visionApiUrl = prefs.getString("vision_api_url", "") ?: "",
            visionApiKey = prefs.getString("vision_api_key", "") ?: "",
            visionModel = prefs.getString("vision_model", "") ?: "",
            imageApiUrl = prefs.getString("image_api_url", "") ?: "",
            imageApiKey = prefs.getString("image_api_key", "") ?: "",
            imageModel = prefs.getString("image_model", "") ?: "",
            systemPrompt = prefs.getString("system_prompt", "") ?: ""
        )
        cached = config
        return config
    }

    fun saveConfig(config: AppConfig) {
        prefs.edit()
            .putString("chat_api_url", config.chatApiUrl.trim())
            .putString("chat_api_key", sanitizeKey(config.chatApiKey))
            .putString("chat_model", config.chatModel.trim())
            .putFloat("temperature", config.temperature)
            .putFloat("top_p", config.topP)
            .putInt("max_tokens", config.maxTokens)
            .putString("vision_api_url", config.visionApiUrl.trim())
            .putString("vision_api_key", sanitizeKey(config.visionApiKey))
            .putString("vision_model", config.visionModel.trim())
            .putString("image_api_url", config.imageApiUrl.trim())
            .putString("image_api_key", sanitizeKey(config.imageApiKey))
            .putString("image_model", config.imageModel.trim())
            .putString("system_prompt", config.systemPrompt)
            .apply()
        cached = null // Invalidate cache
    }

    /** Strip non-ASCII chars that would break HTTP headers */
    private fun sanitizeKey(key: String): String {
        return key.filter { it.code < 128 }.trim()
    }

    fun hasChatConfig(): Boolean {
        val c = loadConfig()
        return c.chatApiUrl.isNotBlank() && c.chatApiKey.isNotBlank() && c.chatModel.isNotBlank()
    }

    fun hasImageConfig(): Boolean {
        val c = loadConfig()
        return c.effectiveImageUrl().isNotBlank() && c.effectiveImageKey().isNotBlank()
    }

    fun hasVisionConfig(): Boolean {
        val c = loadConfig()
        return c.effectiveVisionUrl().isNotBlank() && c.effectiveVisionKey().isNotBlank()
    }
}
