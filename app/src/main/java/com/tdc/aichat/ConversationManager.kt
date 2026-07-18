package com.tdc.aichat

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConversationManager(context: Context) {
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    // Per-conversation message cap to prevent SharedPreferences overflow
    private val maxMessagesPerConv = 500
    // Max total conversations to retain
    private val maxConversations = 200

    // Encrypted storage for conversation data (v5.5+)
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nova_conversations_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Legacy plaintext prefs (for one-time migration from v5.4-)
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("conversations", Context.MODE_PRIVATE)

    private var conversations: MutableList<Conversation> = mutableListOf()
    var currentId: String = ""
    private var savePending = false

    // Cached sorted list, invalidated on mutation
    private var sortedCache: List<Conversation>? = null
    private fun invalidateCache() { sortedCache = null }

    init {
        load()
    }

    fun list(): List<Conversation> {
        sortedCache?.let { return it }
        val sorted = conversations.sortedWith(
            compareByDescending<Conversation> { it.pinned }.thenByDescending { it.createdAt }
        )
        sortedCache = sorted
        return sorted
    }

    fun search(query: String): List<Conversation> {
        if (query.isBlank()) return list()
        val q = query.lowercase()
        return conversations.filter { it.title.lowercase().contains(q) }
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.createdAt })
    }

    /** Full-text search: matches title or any message content */
    fun searchFullText(query: String): List<Conversation> {
        if (query.isBlank()) return list()
        val q = query.lowercase()
        return conversations.filter { conv ->
            conv.title.lowercase().contains(q) ||
            conv.messages.any { it.content.lowercase().contains(q) }
        }.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.createdAt })
    }

    fun getCurrent(): Conversation? = conversations.find { it.id == currentId }

    fun switchTo(id: String): Conversation? {
        val conv = conversations.find { it.id == id } ?: return null
        currentId = id
        save()
        return conv
    }

    fun newConversation(): Conversation {
        val conv = Conversation()
        conversations.add(0, conv)
        currentId = conv.id
        invalidateCache()
        save()
        return conv
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (currentId == id) {
            currentId = conversations.firstOrNull()?.id ?: ""
        }
        invalidateCache()
        save()
    }

    fun updateTitle(id: String, title: String) {
        conversations.find { it.id == id }?.title = title
        invalidateCache()
        save()
    }

    fun pinConversation(id: String) {
        val conv = conversations.find { it.id == id } ?: return
        conv.pinned = !conv.pinned
        invalidateCache()
        save()
    }

    fun renameConversation(id: String, newTitle: String) {
        conversations.find { it.id == id }?.let {
            it.title = newTitle.ifBlank { "未命名" }
        }
        invalidateCache()
        save()
    }

    fun updateMessages(id: String, messages: List<ChatMessage>) {
        conversations.find { it.id == id }?.let {
            it.messages.clear()
            // Cap messages per conversation to prevent SharedPreferences overflow
            val capped = if (messages.size > maxMessagesPerConv) {
                messages.takeLast(maxMessagesPerConv)
            } else {
                messages
            }
            it.messages.addAll(capped)
        }
        invalidateCache()
        save()
    }

    /** Debounced save: writes happen at most every 300ms, coalescing rapid changes */
    fun save() {
        if (savePending) return
        savePending = true
        handler.postDelayed({
            savePending = false
            val json = gson.toJson(conversations)
            securePrefs.edit().putString("conv_list", json).apply()
        }, 300)
    }

    /** Immediate (non-debounced) save — use before process death */
    fun saveNow() {
        handler.removeCallbacksAndMessages(null)
        savePending = false
        val json = gson.toJson(conversations)
        securePrefs.edit().putString("conv_list", json).apply()
    }

    private fun load() {
        try {
            // Try encrypted storage first
            var json = securePrefs.getString("conv_list", null)

            // One-time migration from legacy plaintext storage
            if (json == null) {
                val legacyJson = legacyPrefs.getString("conv_list", null)
                if (legacyJson != null) {
                    json = legacyJson
                    // Migrate to encrypted storage
                    securePrefs.edit().putString("conv_list", legacyJson).apply()
                    // Clear legacy storage after successful migration
                    legacyPrefs.edit().clear().apply()
                }
            }

            if (json != null) {
                val type = object : TypeToken<MutableList<Conversation>>() {}.type
                conversations = gson.fromJson(json, type) ?: mutableListOf()
                // Prune oversized conversations to prevent SharedPreferences overflow
                if (conversations.size > maxConversations) {
                    conversations = conversations.take(maxConversations).toMutableList()
                }
            }
        } catch (e: Exception) {
            // Corrupted data — start fresh
            conversations = mutableListOf()
            securePrefs.edit().remove("conv_list").apply()
        }
        // Ensure at least one conversation exists and currentId is valid
        if (conversations.isEmpty()) {
            val newConv = Conversation(title = "新对话")
            conversations.add(0, newConv)
            currentId = newConv.id
        } else {
            // Validate currentId — if corrupted or stale, fall back to first
            if (currentId.isBlank() || conversations.none { it.id == currentId }) {
                currentId = conversations.first().id
            }
        }
        save()
    }
}
