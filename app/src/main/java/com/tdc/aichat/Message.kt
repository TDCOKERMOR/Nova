package com.tdc.aichat

/** Represents a single chat message, either text or image */
data class ChatMessage(
    val role: String,         // "user" | "assistant" | "system"
    val content: String = "", // text content
    val imageUrl: String? = null, // non-null for image messages
    val imagePrompt: String = ""  // prompt used to generate the image
) {
    val isImage: Boolean get() = imageUrl != null
    val isText: Boolean get() = !isImage
}

// --- API models ---

data class ApiMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @com.google.gson.annotations.SerializedName("top_p")
    val top_p: Float? = null,
    @com.google.gson.annotations.SerializedName("max_tokens")
    val max_tokens: Int? = null
)

data class ChatResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: ApiMessage?
)

/** Full app configuration: chat + image recognition + image generation APIs */
data class AppConfig(
    // Chat API
    val chatApiUrl: String = "",
    val chatApiKey: String = "",
    val chatModel: String = "",
    // Image recognition API (multimodal vision, falls back to chat config)
    val visionApiUrl: String = "",
    val visionApiKey: String = "",
    val visionModel: String = "",
    // Image generation API (falls back to chat config)
    val imageApiUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = "",
    // System prompt for chat (optional, stored in SharedPreferences)
    val systemPrompt: String = "",
    // Model parameters
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 2048
) {
    /** Effective image API URL: if blank, derives from chat URL + /v1/images/generations */
    fun effectiveImageUrl(): String {
        if (imageApiUrl.isNotBlank()) return imageApiUrl
        val base = chatApiUrl.trimEnd('/')
        return if (base.endsWith("/v1")) "$base/images/generations"
               else "$base/v1/images/generations"
    }

    fun effectiveImageKey(): String = imageApiKey.ifBlank { chatApiKey }
    fun effectiveVisionUrl(): String = visionApiUrl.ifBlank { chatApiUrl }
    fun effectiveVisionKey(): String = visionApiKey.ifBlank { chatApiKey }
    fun effectiveVisionModel(): String = visionModel.ifBlank { chatModel }
}

// --- Streaming chat models ---
data class ChatStreamChunk(
    val choices: List<StreamChoice>?
)
data class StreamChoice(
    val delta: StreamDelta?,
    val index: Int? = null
)
data class StreamDelta(
    val content: String?,
    @com.google.gson.annotations.SerializedName("reasoning_content")
    val reasoning_content: String?
)

// --- Image generation models ---

data class ImageGenRequest(
    val model: String,
    val prompt: String,
    val n: Int? = null,
    val size: String? = null,
    @com.google.gson.annotations.SerializedName("rsp_img_type")
    val rsp_img_type: String? = null,
    val response_format: String? = null
)

data class ImageGenResponse(
    val data: List<ImageData>?
)

data class ImageData(
    val url: String? = null,
    @com.google.gson.annotations.SerializedName("image_url")
    val image_url: String? = null,
    val b64_json: String? = null,
    val image: String? = null
) {
    val effectiveUrl: String? get() = url ?: image_url ?: image
}

/** Fallback image response formats for non-OpenAI APIs */
data class AltImageResponse(
    val images: List<ImageData>?,
    val output: List<ImageData>?,
    val url: String?
)

/** Async image job response (e.g. 幻梦API) */
data class ImageJobResponse(
    val request_id: String?,
    val `object`: String?,
    val status: String?,
    val error: ImageJobError?,
    val created_at: Long?,
    val data: List<ImageData>?
)

data class ImageJobError(
    val message: String?,
    val type: String?,
    val code: String?
)

// --- Multimodal (vision) models ---

data class MultimodalContent(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class MultimodalRequest(
    val model: String,
    val messages: List<MultimodalMessage>
)

data class MultimodalMessage(
    val role: String,
    val content: List<MultimodalContent>
)
