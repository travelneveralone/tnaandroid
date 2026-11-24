package com.travelneveralone.tna

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChatOutboxTest {
    private class MemStore : StringStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
    }

    private val chatA = UUID.randomUUID()
    private val chatB = UUID.randomUUID()

    private fun item(chat: UUID, text: String, at: Long) =
        OutboxItem(id = UUID.randomUUID().toString(), chatId = chat.toString(), text = text, author = "Я", createdAtMs = at)

    @Test fun queuedTextSurvivesRestart() {
        val store = MemStore()
        val first = ChatOutbox(store)
        first.enqueue(item(chatA, "без сети", 100))
        val again = ChatOutbox(store)
        assertEquals(listOf("без сети"), again.forChat(chatA).map { it.text })
        assertEquals(MessageStatus.Queued, again.forChat(chatA).single().asMessage().status)
    }

    @Test fun drainSendsInOrderAndEmptiesTheQueue() = runBlocking {
        val box = ChatOutbox(MemStore())
        box.enqueue(item(chatA, "раз", 100))
        box.enqueue(item(chatA, "два", 200))
        val sent = mutableListOf<String>()
        val delivered = box.drain { sent += it.text }
        assertEquals(listOf("раз", "два"), sent)
        assertEquals(2, delivered.size)
        assertTrue(box.items.isEmpty())
    }

    @Test fun aFailureHoldsBackLaterTextsOfThatChatOnly() = runBlocking {
        val box = ChatOutbox(MemStore())
        box.enqueue(item(chatA, "раз", 100))
        box.enqueue(item(chatA, "два", 200))
        box.enqueue(item(chatB, "другой чат", 300))
        val sent = mutableListOf<String>()
        box.drain { if (it.text == "раз") error("offline") else sent += it.text }
        // «два» must not overtake «раз»; the other chat is not held back.
        assertEquals(listOf("другой чат"), sent)
        assertEquals(listOf("раз", "два"), box.forChat(chatA).map { it.text })
        assertEquals(1, box.forChat(chatA).first().attempts)
    }

    @Test fun editAndDeleteWhileWaiting() {
        val box = ChatOutbox(MemStore())
        val waiting = item(chatA, "старое", 100)
        box.enqueue(waiting)
        box.updateText(UUID.fromString(waiting.id), "новое")
        assertEquals("новое", box.items.single().text)
        box.remove(UUID.fromString(waiting.id))
        assertTrue(box.items.isEmpty())
    }

    @Test fun supportTextMovesToTheRealChat() {
        val box = ChatOutbox(MemStore())
        box.enqueue(item(chatA, "в поддержку", 100))
        box.rehome(chatA, chatB)
        assertEquals(1, box.forChat(chatB).size)
        assertTrue(box.forChat(chatA).isEmpty())
    }
}
