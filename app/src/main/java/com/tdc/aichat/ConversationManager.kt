package com.tdc.aichat

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConversationManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var conversations: MutableList<Conversation> = mutableListOf()
    var currentId: String = ""

    init {
        load()
    }

    fun list(): List<Conversation> = conversations.sortedWith(
        compareByDescending<Conversation> { it.pinned }.thenByDescending { it.createdAt }
    )

    fun search(query: String): List<Conversation> {
        if (query.isBlank()) return list()
        val q = query.lowercase()
        return conversations.filter { it.title.lowercase().contains(q) }
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.createdAt })
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
        save()
        return conv
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (currentId == id) {
            currentId = conversations.firstOrNull()?.id ?: ""
        }
        save()
    }

    fun updateTitle(id: String, title: String) {
        conversations.find { it.id == id }?.title = title
        save()
    }

    fun pinConversation(id: String) {
        val conv = conversations.find { it.id == id } ?: return
        conv.pinned = !conv.pinned
        save()
    }

    fun renameConversation(id: String, newTitle: String) {
        conversations.find { it.id == id }?.let {
            it.title = newTitle.ifBlank { "未命名" }
        }
        save()
    }

    fun updateMessages(id: String, messages: List<ChatMessage>) {
        conversations.find { it.id == id }?.let {
            it.messages.clear()
            it.messages.addAll(messages)
        }
        save()
    }

    fun save() {
        val json = gson.toJson(conversations)
        prefs.edit().putString("conv_list", json).apply()
    }

    private fun load() {
        try {
            val json = prefs.getString("conv_list", null)
            if (json != null) {
                val type = object : TypeToken<MutableList<Conversation>>() {}.type
                conversations = gson.fromJson(json, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            // Corrupted data — start fresh
            conversations = mutableListOf()
            prefs.edit().remove("conv_list").apply()
        }
        // Create a new conversation only if none exists
        if (conversations.isEmpty()) {
            val newConv = Conversation(title = "新对话")
            conversations.add(0, newConv)
            currentId = newConv.id
            save()
        } else {
            currentId = conversations.first().id
        }
    }
}
