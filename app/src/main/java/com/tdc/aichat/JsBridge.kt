package com.tdc.aichat

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope

/**
 * JavaScript bridge facade — the single object registered with WebView.
 * Delegates to [ChatBridgeHandler] for chat/conversation logic and
 * [ImageBridgeHandler] for image generation, vision, and file operations.
 */
class JsBridge(
    activity: MainActivity,
    configManager: ConfigManager,
    convManager: ConversationManager,
    scope: CoroutineScope
) {
    private val chat = ChatBridgeHandler(activity, configManager, convManager, scope)
    private val image = ImageBridgeHandler(activity, configManager, scope)
    private val mainActivity = activity

    // ── Stream control ───────────────────────────────

    @JavascriptInterface
    fun cancelCurrentStream() = chat.cancelCurrentStream()

    fun destroy() = chat.destroy()

    // ── Chat ─────────────────────────────────────────

    @JavascriptInterface
    fun sendMessage(json: String) = chat.sendMessage(json)

    @JavascriptInterface
    fun generateTitle(firstMsg: String) = chat.generateTitle(firstMsg)

    // ── Image ────────────────────────────────────────

    @JavascriptInterface
    fun generateImage(prompt: String) = image.generateImage(prompt)

    @JavascriptInterface
    fun optimizePrompt(prompt: String, style: String, size: String) =
        image.optimizePrompt(prompt, style, size)

    @JavascriptInterface
    fun analyzeImage(b64: String, mime: String, request: String) =
        image.analyzeImage(b64, mime, request)

    @JavascriptInterface
    fun downloadImage(url: String, filename: String) =
        image.downloadImage(url, filename)

    // ── File picker ──────────────────────────────────

    @JavascriptInterface
    fun pickImage() {
        mainActivity.pickImageForWeb()
    }

    fun onImagePicked(uri: Uri) = image.onImagePicked(uri)

    // ── Conversation CRUD ────────────────────────────

    @JavascriptInterface
    fun getConversations(): String = chat.getConversations()

    @JavascriptInterface
    fun getCurrentId(): String = chat.getCurrentId()

    @JavascriptInterface
    fun getMessages(): String = chat.getMessages()

    @JavascriptInterface
    fun switchConversation(id: String) = chat.switchConversation(id)

    @JavascriptInterface
    fun newConversation() = chat.newConversation()

    @JavascriptInterface
    fun deleteConversation(id: String) = chat.deleteConversation(id)

    @JavascriptInterface
    fun pinConversation(id: String) = chat.pinConversation(id)

    @JavascriptInterface
    fun isPinned(id: String): Boolean = chat.isPinned(id)

    @JavascriptInterface
    fun renameConversation(id: String, newTitle: String) =
        chat.renameConversation(id, newTitle)

    @JavascriptInterface
    fun updateMessages(json: String) = chat.updateMessages(json)

    @JavascriptInterface
    fun searchConversations(query: String): String = chat.searchConversations(query)

    @JavascriptInterface
    fun searchFullText(query: String): String = chat.searchFullText(query)

    @JavascriptInterface
    fun shareConversation(id: String) = chat.shareConversation(id)

    @JavascriptInterface
    fun shareText(text: String, filename: String) = chat.shareTextAsFile(text, filename)

    @JavascriptInterface
    fun editMessage(msgIndex: String, newContent: String) {
        val idx = msgIndex.toIntOrNull() ?: return
        chat.editMessage(idx, newContent)
    }

    // ── Config ───────────────────────────────────────

    @JavascriptInterface
    fun hasConfig(): Boolean = configManager.hasChatConfig()

    @JavascriptInterface
    fun hasImageConfig(): Boolean = configManager.hasImageConfig()

    @JavascriptInterface
    fun hasVisionConfig(): Boolean = configManager.hasVisionConfig()

    @JavascriptInterface
    fun openSettings() {
        mainActivity.startActivity(Intent(mainActivity, SettingsActivity::class.java))
    }
}
