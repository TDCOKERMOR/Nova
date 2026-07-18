package com.tdc.aichat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ConversationManager
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ConversationManager
    private val gson = Gson()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Use a unique prefs name for each test to avoid interference
        manager = ConversationManager(context)
    }

    @Test
    fun `test new conversation created`() {
        val conv = manager.newConversation()
        
        assertNotNull(conv.id)
        assertEquals("新对话", conv.title)
        assertFalse(conv.pinned)
        assertTrue(conv.messages.isEmpty())
        assertTrue(conv.createdAt > 0)
    }

    @Test
    fun `test list returns conversations sorted by pinned and createdAt`() {
        val conv1 = manager.newConversation()
        conv1.title = "First"
        Thread.sleep(10) // Ensure different timestamps
        val conv2 = manager.newConversation()
        conv2.title = "Second"
        conv2.pinned = true
        manager.saveNow()
        
        val list = manager.list()
        
        assertEquals(2, list.size)
        // Pinned should come first
        assertEquals("Second", list[0].title)
        assertEquals("First", list[1].title)
    }

    @Test
    fun `test switchConversation`() {
        val conv1 = manager.newConversation()
        conv1.title = "Conv1"
        val conv2 = manager.newConversation()
        conv2.title = "Conv2"
        
        val switched = manager.switchTo(conv1.id)
        
        assertNotNull(switched)
        assertEquals("Conv1", switched?.title)
        assertEquals(conv1.id, manager.currentId)
    }

    @Test
    fun `test switchTo non-existent returns null`() {
        val result = manager.switchTo("non-existent-id")
        
        assertNull(result)
    }

    @Test
    fun `test deleteConversation`() {
        val conv1 = manager.newConversation()
        val conv2 = manager.newConversation()
        
        manager.deleteConversation(conv1.id)
        
        val list = manager.list()
        assertEquals(1, list.size)
        assertEquals(conv2.id, list[0].id)
    }

    @Test
    fun `test deleteCurrentConversation switches to next`() {
        val conv1 = manager.newConversation()
        val conv2 = manager.newConversation()
        // Make conv1 current
        manager.switchTo(conv1.id)
        
        manager.deleteConversation(conv1.id)
        
        assertEquals(conv2.id, manager.currentId)
    }

    @Test
    fun `test updateTitle`() {
        val conv = manager.newConversation()
        
        manager.updateTitle(conv.id, "New Title")
        
        val updated = manager.list().find { it.id == conv.id }
        assertEquals("New Title", updated?.title)
    }

    @Test
    fun `test pinConversation toggles`() {
        val conv = manager.newConversation()
        assertFalse(conv.pinned)
        
        manager.pinConversation(conv.id)
        var updated = manager.list().find { it.id == conv.id }
        assertTrue(updated?.pinned ?: false)
        
        manager.pinConversation(conv.id)
        updated = manager.list().find { it.id == conv.id }
        assertFalse(updated?.pinned ?: false)
    }

    @Test
    fun `test renameConversation`() {
        val conv = manager.newConversation()
        
        manager.renameConversation(conv.id, "Renamed")
        
        val updated = manager.list().find { it.id == conv.id }
        assertEquals("Renamed", updated?.title)
    }

    @Test
    fun `test renameConversation with empty title uses default`() {
        val conv = manager.newConversation()
        
        manager.renameConversation(conv.id, "   ")
        
        val updated = manager.list().find { it.id == conv.id }
        assertEquals("未命名", updated?.title)
    }

    @Test
    fun `test updateMessages caps at maxMessagesPerConv`() {
        val conv = manager.newConversation()
        val messages = mutableListOf<ChatMessage>()
        
        // Add more than 500 messages (the limit)
        for (i in 0..550) {
            messages.add(ChatMessage(role = "user", content = "Message $i"))
        }
        
        manager.updateMessages(conv.id, messages)
        
        val updated = manager.getCurrent()
        assertEquals(500, updated?.messages?.size ?: -1)
        // Should keep the last 500
        assertEquals("Message 550", updated?.messages?.last()?.content)
    }

    @Test
    fun `test searchConversations by title`() {
        val conv1 = manager.newConversation()
        conv1.title = "Hello World"
        val conv2 = manager.newConversation()
        conv2.title = "Goodbye World"
        val conv3 = manager.newConversation()
        conv3.title = "Another Chat"
        manager.saveNow()
        
        val results = manager.searchConversations("World")
        
        assertEquals(2, results.size)
        assertTrue(results.any { it.title == "Hello World" })
        assertTrue(results.any { it.title == "Goodbye World" })
    }

    @Test
    fun `test searchConversations case insensitive`() {
        val conv = manager.newConversation()
        conv.title = "Hello World"
        manager.saveNow()
        
        val results = manager.searchConversations("hello")
        
        assertEquals(1, results.size)
    }

    @Test
    fun `test searchFullText searches message content`() {
        val conv1 = manager.newConversation()
        conv1.messages.add(ChatMessage(role = "user", content = "Hello world"))
        val conv2 = manager.newConversation()
        conv2.messages.add(ChatMessage(role = "user", content = "Goodbye world"))
        val conv3 = manager.newConversation()
        conv3.title = "World Chat"
        manager.saveNow()
        
        val results = manager.searchFullText("world")
        
        assertEquals(3, results.size)
    }

    @Test
    fun `test getMessages returns current conversation messages`() {
        val conv = manager.newConversation()
        conv.messages.add(ChatMessage(role = "user", content = "Hello"))
        conv.messages.add(ChatMessage(role = "assistant", content = "Hi there"))
        manager.saveNow()
        
        val json = manager.getMessages()
        val messages = gson.fromJson(json, Array<ChatMessage>::class.java)
        
        assertEquals(2, messages.size)
        assertEquals("Hello", messages[0].content)
        assertEquals("Hi there", messages[1].content)
    }

    @Test
    fun `test getCurrentId returns current conversation id`() {
        val conv = manager.newConversation()
        
        val id = manager.getCurrentId()
        
        assertEquals(conv.id, id)
    }

    @Test
    fun `test isPinned`() {
        val conv = manager.newConversation()
        assertFalse(manager.isPinned(conv.id))
        
        manager.pinConversation(conv.id)
        assertTrue(manager.isPinned(conv.id))
    }
}