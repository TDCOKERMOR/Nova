package com.tdc.aichat

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class JsBridge(
    private val activity: MainActivity,
    private val configManager: ConfigManager,
    private val convManager: ConversationManager,
    private val scope: CoroutineScope
) {
    private val gson = Gson()

    /** Currently running streaming request job — cancelled on new send or destroy */
    @Volatile private var currentJob: Job? = null

    /** Cancel ongoing streaming request (called from WebView on destroy or new conversation) */
    @JavascriptInterface
    fun cancelCurrentStream() {
        currentJob?.cancel()
        currentJob = null
    }

    /** Called from MainActivity.onDestroy to clean up */
    fun destroy() {
        currentJob?.cancel()
        currentJob = null
    }

    // ── API calls ──────────────────────────────────────

    @JavascriptInterface
    fun sendMessage(json: String) {
        // Cancel any previous streaming request
        currentJob?.cancel()
        currentJob = scope.launch {
            val data = gson.fromJson(json, SendMsgData::class.java)
            val config = configManager.loadConfig()
            val apiMsgs = data.messages.map { ApiMessage(role = it.role, content = it.content) }
            val msgId = "msg_" + System.currentTimeMillis()
            activity.runOnUiThread {
                activity.webView.evaluateJavascript(
                    "onChatStreamStart('$msgId')", null
                )
            }
            val result = ApiClient.sendMessageStream(config, apiMsgs) { batch, _isFirst ->
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript(
                        "onChatStreamBatch('$msgId','${escapeJs(batch)}')", null
                    )
                }
            }
            result.fold(
                onSuccess = {
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onChatStreamEnd('$msgId')", null
                        )
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        // Clean up streaming bubble on cancel
                        activity.runOnUiThread {
                            activity.webView.evaluateJavascript(
                                "onChatStreamCancel('$msgId')", null
                            )
                        }
                        return@fold
                    }
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onChatStreamError('$msgId','${escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
        currentJob = currentJob.also { /* capture in outer scope */ }
    }

    @JavascriptInterface
    fun generateImage(prompt: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.generateImage(config, prompt)
            result.fold(
                onSuccess = { url ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageResult('${escapeJs(url)}','${escapeJs(prompt)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageError('${escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun optimizePrompt(prompt: String, style: String, size: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val extra = buildString {
                if (style.isNotEmpty()) append("风格要求：$style。")
                if (size.isNotEmpty()) append("目标尺寸：$size。")
            }
            val fullPrompt = if (extra.isNotEmpty()) "$prompt。$extra" else prompt
            val result = ApiClient.optimizePrompt(config, fullPrompt)
            result.fold(
                onSuccess = { optimized ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onOptimizeResult('${escapeJs(optimized)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onOptimizeError('${escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun analyzeImage(b64: String, mime: String, request: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.analyzeImageAndGeneratePrompt(config, b64, mime, request)
            result.fold(
                onSuccess = { prompt ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageAnalysisResult('${escapeJs(prompt)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageAnalysisError('${escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun generateTitle(firstMsg: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val msgs = listOf(
                ApiMessage(role = "system", content = "你是一个标题生成助手。根据用户的第一条消息，生成一个简短的对话标题（6-12个字）。只输出标题，不要有任何其他内容。"),
                ApiMessage(role = "user", content = firstMsg)
            )
            val result = ApiClient.sendMessage(config, msgs)
            result.onSuccess { title ->
                val t = title.trim().replace("\"", "").take(20)
                if (t.isNotBlank()) {
                    convManager.updateTitle(convManager.currentId, t)
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript("refreshSidebar()", null)
                    }
                }
            }
        }
    }

    // ── Conversation management ─────────────────────────

    @JavascriptInterface
    fun getConversations(): String = gson.toJson(convManager.list())

    @JavascriptInterface
    fun getCurrentId(): String = convManager.currentId

    @JavascriptInterface
    fun getMessages(): String {
        val conv = convManager.getCurrent()
        return gson.toJson(conv?.messages ?: emptyList<ChatMessage>())
    }

    @JavascriptInterface
    fun switchConversation(id: String) {
        convManager.switchTo(id)
    }

    @JavascriptInterface
    fun newConversation() {
        convManager.newConversation()
    }

    @JavascriptInterface
    fun deleteConversation(id: String) {
        convManager.deleteConversation(id)
    }

    @JavascriptInterface
    fun pinConversation(id: String) {
        convManager.pinConversation(id)
    }

    @JavascriptInterface
    fun isPinned(id: String): Boolean {
        return convManager.list().find { it.id == id }?.pinned ?: false
    }

    @JavascriptInterface
    fun renameConversation(id: String, newTitle: String) {
        convManager.renameConversation(id, newTitle)
    }

    @JavascriptInterface
    fun updateMessages(json: String) {
        val type = object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
        val msgs: List<ChatMessage> = gson.fromJson(json, type)
        convManager.updateMessages(convManager.currentId, msgs)
    }

    @JavascriptInterface
    fun searchConversations(query: String): String {
        return gson.toJson(convManager.search(query))
    }

    // ── Config ──────────────────────────────────────────

    @JavascriptInterface
    fun hasConfig(): Boolean = configManager.hasChatConfig()
    @JavascriptInterface
    fun hasImageConfig(): Boolean = configManager.hasImageConfig()
    @JavascriptInterface
    fun hasVisionConfig(): Boolean = configManager.hasVisionConfig()

    @JavascriptInterface
    fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    // ── File picker trigger ─────────────────────────────

    @JavascriptInterface
    fun pickImage() {
        activity.pickImageForWeb()
    }

    fun onImagePicked(uri: Uri) {
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val input: InputStream = activity.contentResolver.openInputStream(uri) ?: return@withContext null
                    val bitmap = android.graphics.BitmapFactory.decodeStream(input); input.close()
                    if (bitmap == null) return@withContext null
                    val maxDim = 2048
                    val w = bitmap.width; val h = bitmap.height
                    val scaled = if (w > maxDim || h > maxDim) {
                        val r = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                        android.graphics.Bitmap.createScaledBitmap(bitmap, (w * r).toInt(), (h * r).toInt(), true)
                    } else bitmap
                    val bos = ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
                    val data = bos.toByteArray(); bos.close()
                    if (scaled !== bitmap) scaled.recycle(); bitmap.recycle()
                    data
                }
                if (bytes != null) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImagePicked('$b64','image/jpeg',${bytes.size})", null
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Image download ───────────────────────────────────

    @JavascriptInterface
    fun downloadImage(url: String, filename: String) {
        scope.launch {
            try {
                val displayName = filename.ifBlank { "AIChat_${System.currentTimeMillis()}.png" }
                val mime = if (displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true)) "image/jpeg" else "image/png"

                if (url.startsWith("data:image")) {
                    saveBase64Image(url, displayName, mime)
                } else {
                    downloadUrlImage(url, displayName, mime)
                }
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript("onDownloadResult('已保存到下载目录: $displayName')", null)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript("onDownloadResult('保存失败: ${escapeJs(e.message ?: "error")}')", null)
                }
            }
        }
    }

    private suspend fun saveBase64Image(dataUrl: String, filename: String, mime: String) = withContext(Dispatchers.IO) {
        val comma = dataUrl.indexOf(',')
        val b64 = if (comma > 0) dataUrl.substring(comma + 1) else dataUrl
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        writeImageToDownloads(bytes, filename, mime)
    }

    private suspend fun downloadUrlImage(url: String, filename: String, mime: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        val resp = ApiClient.client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        val bytes = resp.body?.bytes() ?: throw Exception("empty body")
        writeImageToDownloads(bytes, filename, mime)
    }

    private fun writeImageToDownloads(bytes: ByteArray, filename: String, mime: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = activity.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法创建文件")
            activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw Exception("无法写入文件")
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, filename)
            file.writeBytes(bytes)
        }
    }

    // ── Helpers ──────────────────────────────────────────

    /**
     * Safe JS string escaping for evaluateJavascript.
     * Encodes all characters that could break out of a JS string literal.
     */
    private fun escapeJs(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\\'")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\f' -> sb.append("\\f")
                else -> {
                    if (c.code < 32 || c.code > 126) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    data class SendMsgData(
        val messages: List<JsMessage>
    )
    data class JsMessage(
        val role: String,
        val content: String
    )
}
