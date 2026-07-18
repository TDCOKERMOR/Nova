package com.tdc.aichat

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Handles chat streaming, title generation, and conversation management.
 * Called from JsBridge facade, not directly exposed to WebView.
 */
class ChatBridgeHandler(
    private val activity: MainActivity,
    private val configManager: ConfigManager,
    private val convManager: ConversationManager,
    private val scope: CoroutineScope
) {
    private val gson = Gson()

    @Volatile var currentJob: Job? = null

    fun cancelCurrentStream() {
        currentJob?.cancel()
        currentJob = null
    }

    fun destroy() {
        currentJob?.cancel()
        currentJob = null
    }

    // ── Chat ─────────────────────────────────────────

    fun sendMessage(json: String) {
        currentJob?.cancel()
        val data = gson.fromJson(json, SendMsgData::class.java)
        currentJob = runStream(data.messages)
    }

    /** Shared streaming pipeline: prepend system prompt, then send via ApiClient */
    private fun runStream(jsMessages: List<JsMessage>): Job = scope.launch {
        val config = configManager.loadConfig()
        val msgList = jsMessages.toMutableList()
        if (config.systemPrompt.isNotBlank() && msgList.none { it.role == "system" }) {
            msgList.add(0, JsMessage(role = "system", content = config.systemPrompt))
        }
        val apiMsgs = msgList.map { ApiMessage(role = it.role, content = it.content) }
        val msgId = "msg_" + System.currentTimeMillis()
        activity.runOnUiThread {
            activity.webView.evaluateJavascript("onChatStreamStart('$msgId')", null)
        }
        val result = ApiClient.sendMessageStream(config, apiMsgs) { batch, _ ->
            activity.runOnUiThread {
                activity.webView.evaluateJavascript(
                    "onChatStreamBatch('$msgId','${escapeJs(batch)}')", null
                )
            }
        }
        result.fold(
            onSuccess = {
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript("onChatStreamEnd('$msgId')", null)
                }
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript("onChatStreamCancel('$msgId')", null)
                    }
                    return@fold
                }
                val errMsg = error.message ?: "未知错误"
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript(
                        "onChatStreamError('$msgId','${escapeJs(errMsg)}')", null
                    )
                }
            }
        )
    }

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

    // ── Conversation management ──────────────────────

    fun getConversations(): String = gson.toJson(convManager.list())

    fun getCurrentId(): String = convManager.currentId

    fun getMessages(): String {
        val conv = convManager.getCurrent()
        return gson.toJson(conv?.messages ?: emptyList<ChatMessage>())
    }

    fun switchConversation(id: String) {
        convManager.switchTo(id)
    }

    fun newConversation() {
        convManager.newConversation()
    }

    fun deleteConversation(id: String) {
        convManager.deleteConversation(id)
    }

    fun pinConversation(id: String) {
        convManager.pinConversation(id)
    }

    fun isPinned(id: String): Boolean {
        return convManager.list().find { it.id == id }?.pinned ?: false
    }

    fun renameConversation(id: String, newTitle: String) {
        convManager.renameConversation(id, newTitle)
    }

    fun updateMessages(json: String) {
        val type = object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
        val msgs: List<ChatMessage> = gson.fromJson(json, type)
        convManager.updateMessages(convManager.currentId, msgs)
    }

    fun searchConversations(query: String): String {
        return gson.toJson(convManager.search(query))
    }

    /** Full-text search including message content */
    fun searchFullText(query: String): String {
        return gson.toJson(convManager.searchFullText(query))
    }

    fun shareConversation(id: String) {
        val conv = convManager.list().find { it.id == id } ?: return
        val msgs = conv.messages.filter { it.imageUrl == null }
            .map { mapOf("role" to it.role, "content" to it.content) }
        val json = gson.toJson(msgs)
        shareTextAsFile(json, "conversation.json")
    }

    fun shareTextAsFile(text: String, filename: String) {
        try {
            val dir = java.io.File(activity.cacheDir, "shared")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, filename)
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(android.content.Intent.createChooser(intent, "分享对话"))
        } catch (e: Exception) {
            // Fallback: share as text if file creation fails
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            activity.startActivity(android.content.Intent.createChooser(intent, "分享对话"))
        }
    }

    // ── Key-pair editing ───────────────────────────

    /** Edit a specific user message and regenerate the assistant reply */
    fun editMessage(msgIndex: Int, newContent: String) {
        val conv = convManager.getCurrent() ?: return
        val msgs = conv.messages
        if (msgIndex < 0 || msgIndex >= msgs.size) return
        if (msgs[msgIndex].role != "user") return

        // Cancel any in-flight stream before mutating state
        currentJob?.cancel()
        currentJob = null

        // Update the edited message and truncate everything after it
        msgs[msgIndex] = msgs[msgIndex].copy(content = newContent)
        val keepCount = msgIndex + 1
        while (msgs.size > keepCount) msgs.removeAt(msgs.size - 1)
        convManager.updateMessages(conv.id, msgs)

        // Notify WebView to reload the truncated message list
        activity.runOnUiThread {
            activity.webView.evaluateJavascript("loadMessages()", null)
        }

        // Re-send from the edited message using shared pipeline
        val apiMsgs = msgs.filter { it.imageUrl == null }
            .map { JsMessage(it.role, it.content) }
        currentJob = runStream(apiMsgs)
    }

    // ── Helpers ──────────────────────────────────────

    companion object {
        fun escapeJs(s: String): String {
            val sb = StringBuilder(s.length + 16)
            for (c in s) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '\'' -> sb.append("\\'")
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
    }

    data class SendMsgData(val messages: List<JsMessage>)
    data class JsMessage(val role: String, val content: String)
}
