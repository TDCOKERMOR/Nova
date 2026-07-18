package com.tdc.aichat

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ConfigManager
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConfigManagerTest {

    private lateinit var context: Context
    private lateinit var configManager: ConfigManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Clear encrypted shared preferences
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "ai_chat_config_enc",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            // Ignore if not exists
        }
        
        configManager = ConfigManager(context)
    }

    @Test
    fun `test default config`() {
        val config = configManager.loadConfig()
        
        assertEquals("", config.chatApiUrl)
        assertEquals("", config.chatApiKey)
        assertEquals("", config.chatModel)
        assertEquals(0.7f, config.temperature, 0.001f)
        assertEquals(1.0f, config.topP, 0.001f)
        assertEquals(2048, config.maxTokens)
        assertEquals("", config.systemPrompt)
    }

    @Test
    fun `test save and load config`() {
        val config = AppConfig(
            chatApiUrl = "https://api.example.com/v1",
            chatApiKey = "test-key-123",
            chatModel = "gpt-4",
            temperature = 0.5f,
            topP = 0.9f,
            maxTokens = 4096,
            visionApiUrl = "https://vision.example.com/v1",
            visionApiKey = "vision-key",
            visionModel = "gpt-4o",
            imageApiUrl = "https://image.example.com/v1/images",
            imageApiKey = "image-key",
            imageModel = "dall-e-3",
            systemPrompt = "You are a helpful assistant"
        )
        
        configManager.saveConfig(config)
        val loaded = configManager.loadConfig()
        
        assertEquals("https://api.example.com/v1", loaded.chatApiUrl)
        assertEquals("test-key-123", loaded.chatApiKey)
        assertEquals("gpt-4", loaded.chatModel)
        assertEquals(0.5f, loaded.temperature, 0.001f)
        assertEquals(0.9f, loaded.topP, 0.001f)
        assertEquals(4096, loaded.maxTokens)
        assertEquals("https://vision.example.com/v1", loaded.visionApiUrl)
        assertEquals("vision-key", loaded.visionApiKey)
        assertEquals("gpt-4o", loaded.visionModel)
        assertEquals("https://image.example.com/v1/images", loaded.imageApiUrl)
        assertEquals("image-key", loaded.imageApiKey)
        assertEquals("dall-e-3", loaded.imageModel)
        assertEquals("You are a helpful assistant", loaded.systemPrompt)
    }

    @Test
    fun `test hasChatConfig`() {
        // Initially no config
        assertFalse(configManager.hasChatConfig())
        
        // Save minimal chat config
        val config = AppConfig(
            chatApiUrl = "https://api.example.com",
            chatApiKey = "key",
            chatModel = "model"
        )
        configManager.saveConfig(config)
        
        assertTrue(configManager.hasChatConfig())
    }

    @Test
    fun `test hasImageConfig`() {
        assertFalse(configManager.hasImageConfig())
        
        val config = AppConfig(
            chatApiUrl = "https://api.example.com",
            chatApiKey = "key",
            chatModel = "model",
            imageApiUrl = "https://image.example.com",
            imageApiKey = "image-key",
            imageModel = "dall-e"
        )
        configManager.saveConfig(config)
        
        assertTrue(configManager.hasImageConfig())
    }

    @Test
    fun `test hasVisionConfig`() {
        assertFalse(configManager.hasVisionConfig())
        
        val config = AppConfig(
            chatApiUrl = "https://api.example.com",
            chatApiKey = "key",
            chatModel = "model",
            visionApiUrl = "https://vision.example.com",
            visionApiKey = "vision-key",
            visionModel = "gpt-4o"
        )
        configManager.saveConfig(config)
        
        assertTrue(configManager.hasVisionConfig())
    }

    @Test
    fun `test sanitize key removes non-ascii`() {
        // Test via saving and loading - non-ASCII should be stripped
        val config = AppConfig(
            chatApiUrl = "https://api.example.com",
            chatApiKey = "key-with-中文-chars",
            chatModel = "model"
        )
        configManager.saveConfig(config)
        
        val loaded = configManager.loadConfig()
        // Non-ASCII chars should be removed
        assertFalse(loaded.chatApiKey.contains("中文"))
        assertTrue(loaded.chatApiKey.contains("key-with-"))
        assertTrue(loaded.chatApiKey.contains("-chars"))
    }
}