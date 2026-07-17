package com.tdc.aichat

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiClient {
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    internal val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Strip any non-ASCII garbage from API keys that would break HTTP headers */
    private fun cleanKey(key: String): String = key.filter { it.code < 128 }

    /** Build user-friendly error message from HTTP status code */
    private fun httpErrorMsg(code: Int, service: String): String = when (code) {
        401 -> "$service Key 无效，请检查设置"
        403 -> "$service 访问被拒绝，请检查权限"
        404 -> "$service 地址不存在，请检查 URL 配置"
        429 -> "请求过于频繁，请稍后重试"
        500 -> "服务器内部错误，请稍后重试"
        502 -> "网关错误，$service 可能暂时不可用"
        503 -> "$service 暂时不可用，请稍后重试"
        else -> "$service HTTP $code"
    }

    /**
     * Send chat message with streaming.
     * Supports both regular content and reasoning_content (DeepSeek-R1 style thinking).
     * Tokens are batched and delivered every ~50ms to reduce UI thread thrashing.
     * The callback receives (batch, isFirst).
     * Special markers: \u0000RSTART\u0000 and \u0000REND\u0000 delimit reasoning blocks.
     * Respects coroutine cancellation.
     */
    suspend fun sendMessageStream(
        config: AppConfig,
        messages: List<ApiMessage>,
        onBatch: (batch: String, isFirst: Boolean) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatRequest = ChatRequest(
                model = config.chatModel,
                messages = messages,
                stream = true,
                temperature = config.temperature.takeIf { it != 0.7f },
                top_p = config.topP.takeIf { it != 1.0f },
                max_tokens = config.maxTokens.takeIf { it != 2048 }
            )
            val body = gson.toJson(chatRequest).toRequestBody(JSON)
            val baseUrl = config.chatApiUrl.trimEnd('/')
            val fullUrl = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions"
                          else "$baseUrl/v1/chat/completions"

            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer ${cleanKey(config.chatApiKey)}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(httpErrorMsg(response.code, "对话API")))
            }
            val source = response.body?.source() ?: return@withContext Result.failure(Exception("empty body"))

            val fullContent = StringBuilder()
            val fullReasoning = StringBuilder()
            val batchBuffer = StringBuilder()
            var lastFlush = System.currentTimeMillis()
            var isFirst = true
            var inReasoning = false

            while (!source.exhausted()) {
                kotlinx.coroutines.ensureActive()

                val line = source.readUtf8Line() ?: continue
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = gson.fromJson(data, ChatStreamChunk::class.java)
                        val delta = obj.choices?.firstOrNull()?.delta ?: continue
                        val token = delta.content ?: ""
                        val reasoningToken = delta.reasoning_content ?: ""

                        // Handle reasoning_content (DeepSeek-R1 thinking chain)
                        if (reasoningToken.isNotEmpty()) {
                            if (!inReasoning) {
                                inReasoning = true
                                batchBuffer.clear()
                                withContext(Dispatchers.Main) { onBatch("\u0000RSTART\u0000", isFirst) }
                                isFirst = false
                            }
                            fullReasoning.append(reasoningToken)
                            batchBuffer.append(reasoningToken)
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= 50 || batchBuffer.length >= 128) {
                                val batch = batchBuffer.toString()
                                batchBuffer.clear()
                                lastFlush = now
                                withContext(Dispatchers.Main) { onBatch(batch, isFirst) }
                                isFirst = false
                            }
                        }

                        if (token.isNotEmpty()) {
                            if (inReasoning) {
                                inReasoning = false
                                // Flush remaining reasoning then send end marker
                                if (batchBuffer.isNotEmpty()) {
                                    withContext(Dispatchers.Main) { onBatch(batchBuffer.toString(), isFirst) }
                                    isFirst = false
                                }
                                batchBuffer.clear()
                                withContext(Dispatchers.Main) { onBatch("\u0000REND\u0000", isFirst) }
                                isFirst = false
                            }
                            fullContent.append(token)
                            batchBuffer.append(token)
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= 50 || batchBuffer.length >= 128) {
                                val batch = batchBuffer.toString()
                                batchBuffer.clear()
                                lastFlush = now
                                withContext(Dispatchers.Main) { onBatch(batch, isFirst) }
                                isFirst = false
                            }
                        }
                    } catch (e: com.google.gson.JsonSyntaxException) {
                        // Malformed SSE line — skip gracefully but don't silently swallow
                        android.util.Log.w("Nova", "Stream parse skip: ${e.message?.take(80)}")
                    } catch (e: com.google.gson.JsonIOException) {
                        android.util.Log.w("Nova", "Stream IO error: ${e.message?.take(80)}")
                    }
                }
            }
            // Flush remaining
            if (batchBuffer.isNotEmpty()) {
                withContext(Dispatchers.Main) { onBatch(batchBuffer.toString(), isFirst) }
            }
            Result.success(fullContent.toString())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Send chat message using chat API config (non-streaming) */
    suspend fun sendMessage(
        config: AppConfig,
        messages: List<ApiMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatRequest = ChatRequest(model = config.chatModel, messages = messages)
            val body = gson.toJson(chatRequest).toRequestBody(JSON)
            val baseUrl = config.chatApiUrl.trimEnd('/')
            val fullUrl = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions"
                          else "$baseUrl/v1/chat/completions"

            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer ${cleanKey(config.chatApiKey)}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                if (!responseBody.trimStart().startsWith("{")) {
                    return@withContext Result.failure(Exception("服务器返回了非标准响应，请检查 API 地址"))
                }
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                val reply = chatResponse.choices?.firstOrNull()?.message?.content ?: "(empty)"
                Result.success(reply)
            } else {
                Result.failure(Exception(httpErrorMsg(response.code, "对话API")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Generate image using independent image API config */
    suspend fun generateImage(
        config: AppConfig,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isMaas = config.effectiveImageUrl().contains("maas")
            val imgRequest = ImageGenRequest(
                model = config.imageModel.ifBlank { "dall-e-3" },
                prompt = prompt,
                size = if (isMaas) null else "1024x1024",
                rsp_img_type = if (isMaas) "url" else null
            )
            val body = gson.toJson(imgRequest).toRequestBody(JSON)
            val fullUrl = config.effectiveImageUrl()

            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer ${cleanKey(config.effectiveImageKey())}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                if (!responseBody.trimStart().startsWith("{")) {
                    return@withContext Result.failure(Exception("图片API返回了非标准响应，请检查图片API地址"))
                }
                var imageUrl = extractImageUrl(responseBody)
                var pollError: String? = null
                if (imageUrl == null) {
                    val pr = pollAsyncJob(responseBody, fullUrl, config)
                    if (pr.url != null) {
                        imageUrl = pr.url
                    } else {
                        pollError = pr.error
                    }
                }

                if (imageUrl == null) {
                    return@withContext Result.failure(Exception(pollError ?: "未找到图片URL"))
                }
                Result.success(imageUrl)
            } else {
                Result.failure(Exception(httpErrorMsg(response.code, "图片API")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Walk a JSON object recursively to find a URL string for an image */
    private fun extractImageUrl(json: String): String? {
        try {
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java) ?: return null
            return findUrlIn(root)
        } catch (_: Exception) { return null }
    }

    private fun findUrlIn(el: com.google.gson.JsonElement): String? {
        when {
            el.isJsonObject -> {
                val obj = el.asJsonObject
                for (key in listOf("url", "image_url", "image", "b64_json", "src")) {
                    val v = obj.get(key)
                    if (v != null && v.isJsonPrimitive && !v.asString.startsWith("{"))
                        return v.asString
                }
                for ((_, v) in obj.entrySet()) {
                    findUrlIn(v)?.let { return it }
                }
            }
            el.isJsonArray -> {
                for (item in el.asJsonArray) {
                    findUrlIn(item)?.let { return it }
                }
            }
        }
        return null
    }

    /** Try async job polling if response looks like a job ticket */
    private suspend fun pollAsyncJob(responseBody: String, submitUrl: String, config: AppConfig): PollResult {
        try {
            val jobResp = gson.fromJson(responseBody, ImageJobResponse::class.java) ?: return PollResult(null, "无法解析任务响应")
            if (jobResp.`object` != "image_job" && jobResp.status == null) return PollResult(null, "不是异步任务响应")
            if (jobResp.status == "failed" || jobResp.error != null) return PollResult(null, jobResp.error?.message ?: "任务失败")
            if (jobResp.request_id == null) return PollResult(null, "缺少 request_id")
            return pollImageJob(submitUrl, config, jobResp.request_id)
        } catch (e: Exception) { return PollResult(null, e.message) }
    }

    /** Poll async image job (up to 30s) for completion */
    private suspend fun pollImageJob(
        submitUrl: String,
        config: AppConfig,
        requestId: String
    ): PollResult = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<String>()
        if (submitUrl.contains("/images/")) {
            candidates.add(submitUrl.replace(Regex("/images/[^/?]*"), "/images/jobs/$requestId"))
        }
        candidates.add("${submitUrl.trimEnd('/')}/$requestId")
        candidates.add(submitUrl.replace(Regex("/[^/]*$"), "/jobs/$requestId"))
        candidates.add(submitUrl)

        var lastError = ""
        for (attempt in 0 until 12) {
            delay(2500)
            for (queryUrl in candidates) {
                try {
                    val req = Request.Builder()
                        .url(queryUrl)
                        .addHeader("Authorization", "Bearer ${cleanKey(config.effectiveImageKey())}")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: continue
                    lastError = "[$queryUrl] HTTP ${resp.code}: ${body.take(200)}"
                    if (!body.trimStart().startsWith("{")) continue
                    val job = gson.fromJson(body, ImageJobResponse::class.java)
                    if (job.data != null) {
                        val d = job.data.firstOrNull()
                        if (d != null) return@withContext PollResult(d.effectiveUrl ?: d.b64_json, null)
                    }
                    if (job.status == "success" || job.status == "succeeded") {
                        val url = extractImageUrl(body)
                        if (url != null) return@withContext PollResult(url, null)
                    }
                    if (job.status == "failed") return@withContext PollResult(null, job.error?.message ?: "任务失败")
                } catch (_: Exception) { continue }
            }
        }
        PollResult(null, "轮询超时")
    }

    private data class PollResult(val url: String?, val error: String?)

    /** Analyze image + text via multimodal chat, return a generated image prompt */
    suspend fun analyzeImageAndGeneratePrompt(
        config: AppConfig,
        imageBase64: String,
        mimeType: String,
        userRequest: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = listOf(
                MultimodalContent(type = "text", text = """你是一个专业的图像编辑顾问。
用户上传了一张图片并提出了修改要求。
请根据图片内容和用户的要求，生成一个详细的、可以直接用于AI图像生成模型的英文提示词。
要求：
1. 描述图片中可见的内容（主体、场景、色彩、风格等）
2. 结合修改要求，描述修改后的最终画面
3. 用英文输出
4. 只输出最终的提示词，不要有任何解释"""),
                MultimodalContent(
                    type = "image_url",
                    image_url = ImageUrl(url = "data:$mimeType;base64,$imageBase64")
                ),
                MultimodalContent(type = "text", text = "修改要求：$userRequest")
            )

            val visionModel = config.effectiveVisionModel()
            val visionUrl = config.effectiveVisionUrl().trimEnd('/')
            val visionKey = config.effectiveVisionKey()

            val req = MultimodalRequest(
                model = visionModel,
                messages = listOf(MultimodalMessage(role = "user", content = content))
            )

            val body = gson.toJson(req).toRequestBody(JSON)
            val fullUrl = if (visionUrl.endsWith("/v1")) "$visionUrl/chat/completions"
                          else "$visionUrl/v1/chat/completions"

            val httpReq = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer ${cleanKey(visionKey)}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(httpReq).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                val reply = chatResponse.choices?.firstOrNull()?.message?.content ?: "(empty)"
                Result.success(reply)
            } else {
                Result.failure(Exception(httpErrorMsg(response.code, "视觉API")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Optimize a text prompt */
    suspend fun optimizePrompt(
        config: AppConfig,
        rawPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val systemPrompt = """你是一个专业的提示词优化专家。用户会给你一个简短的描述，你需要将其扩展为详细、高质量的图像生成提示词。
要求：
1. 用英文输出
2. 包含详细的视觉描述：主体、风格、光照、色彩、构图、氛围
3. 添加适当的质量关键词
4. 保持在一段话以内，不要分段
5. 直接输出优化后的提示词，不要加任何解释"""

        val messages = listOf(
            ApiMessage(role = "system", content = systemPrompt),
            ApiMessage(role = "user", content = "请优化以下提示词：$rawPrompt")
        )
        sendMessage(config, messages)
    }
}
