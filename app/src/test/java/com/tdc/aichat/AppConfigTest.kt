package com.tdc.aichat

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for AppConfig data class and related models
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppConfigTest {

    private val gson = Gson()

    @Test
    fun `test AppConfig default values`() {
        val config = AppConfig()
        
        assertEquals("", config.chatApiUrl)
        assertEquals("", config.chatApiKey)
        assertEquals("", config.chatModel)
        assertEquals(0.7f, config.temperature, 0.001f)
        assertEquals(1.0f, config.topP, 0.001f)
        assertEquals(2048, config.maxTokens)
        assertEquals("", config.systemPrompt)
    }

    @Test
    fun `test AppConfig effectiveImageUrl with custom URL`() {
        val config = AppConfig(imageApiUrl = "https://custom.api/v1/images")
        
        assertEquals("https://custom.api/v1/images", config.effectiveImageUrl())
    }

    @Test
    fun `test AppConfig effectiveImageUrl derives from chat URL with /v1`() {
        val config = AppConfig(chatApiUrl = "https://api.example.com/v1")
        
        assertEquals("https://api.example.com/v1/images/generations", config.effectiveImageUrl())
    }

    @Test
    fun `test AppConfig effectiveImageUrl derives from chat URL without /v1`() {
        val config = AppConfig(chatApiUrl = "https://api.example.com/")
        
        assertEquals("https://api.example.com/v1/images/generations", config.effectiveImageUrl())
    }

    @Test
    fun `test AppConfig effectiveImageKey falls back to chatApiKey`() {
        val config = AppConfig(chatApiKey = "chat-key", imageApiKey = "")
        
        assertEquals("chat-key", config.effectiveImageKey())
    }

    @Test
    fun `test AppConfig effectiveVisionUrl falls back to chatApiUrl`() {
        val config = AppConfig(chatApiUrl = "https://api.example.com/v1", visionApiUrl = "")
        
        assertEquals("https://api.example.com/v1", config.effectiveVisionUrl())
    }

    @Test
    fun `test ChatMessage data class`() {
        val msg = ChatMessage(role = "user", content = "Hello")
        
        assertEquals("user", msg.role)
        assertEquals("Hello", msg.content)
        assertNull(msg.imageUrl)
        assertFalse(msg.isImage)
        assertTrue(msg.isText)
    }

    @Test
    fun `test ChatMessage with image`() {
        val msg = ChatMessage(role = "assistant", content = "", imageUrl = "https://example.com/img.png", imagePrompt = "test prompt")
        
        assertTrue(msg.isImage)
        assertFalse(msg.isText)
        assertEquals("https://example.com/img.png", msg.imageUrl)
        assertEquals("test prompt", msg.imagePrompt)
    }

    @Test
    fun `test ApiMessage serialization`() {
        val msg = ApiMessage(role = "user", content = "test")
        val json = gson.toJson(msg)
        val parsed = gson.fromJson(json, ApiMessage::class.java)
        
        assertEquals("user", parsed.role)
        assertEquals("test", parsed.content)
    }

    @Test
    fun `test ChatRequest serialization`() {
        val request = ChatRequest(
            model = "gpt-4",
            messages = listOf(ApiMessage(role = "user", content = "Hello")),
            stream = true,
            temperature = 0.7f,
            top_p = 1.0f,
            max_tokens = 2048
        )
        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"model\":\"gpt-4\""))
        assertTrue(json.contains("\"stream\":true"))
        assertTrue(json.contains("\"temperature\":0.7"))
    }

    @Test
    fun `test ImageGenRequest serialization`() {
        val request = ImageGenRequest(
            model = "dall-e-3",
            prompt = "A beautiful sunset",
            n = 1,
            size = "1024x1024",
            rsp_img_type = "url"
        )
        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"model\":\"dall-e-3\""))
        assertTrue(json.contains("\"prompt\":\"A beautiful sunset\""))
    }
}