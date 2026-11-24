package com.travelneveralone.tna

import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

// Port of TNAiOS Core/Offline/Outbox: unsent chat texts survive a dead
// network, leaving the chat and a restart, and go out by themselves, in the
// order they were written, once the network is back (TNA-156/157).

/** One text waiting to be delivered. `id` is the message id and the server's
 *  idempotency key, so a retry after a lost answer never doubles the message. */
@Serializable
data class OutboxItem(
    val id: String,
    val chatId: String,
    val text: String,
    val author: String,
    val createdAtMs: Long,
    val replyTo: String? = null,
    val attempts: Int = 0,
) {
    /** The bubble shown while it waits. */
    fun asMessage(): Message = Message(
        id = UUID.fromString(id), text = text, isMine = true, author = author,
        time = displayTime(Instant.ofEpochMilli(createdAtMs).toString()),
        status = MessageStatus.Queued, createdAt = Instant.ofEpochMilli(createdAtMs),
        replyTo = replyTo?.let(UUID::fromString),
    )
}

class ChatOutbox(private val store: StringStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val draining = Mutex()

    var items: List<OutboxItem> = load()
        private set

    fun forChat(chatId: UUID): List<OutboxItem> = items.filter { it.chatId == chatId.toString() }

    fun contains(id: UUID): Boolean = items.any { it.id == id.toString() }

    fun enqueue(item: OutboxItem) {
        if (items.any { it.id == item.id }) return
        items = items + item
        persist()
    }

    fun remove(id: UUID) {
        if (!contains(id)) return
        items = items.filterNot { it.id == id.toString() }
        persist()
    }

    /** Edit a text that is still waiting: the edited version is what goes out. */
    fun updateText(id: UUID, text: String) {
        if (!contains(id)) return
        items = items.map { if (it.id == id.toString()) it.copy(text = text) else it }
        persist()
    }

    /** The support chat is created on first use: a text written before that
     *  waits under the placeholder id and moves to the real chat here. */
    fun rehome(from: UUID, to: UUID) {
        if (items.none { it.chatId == from.toString() }) return
        items = items.map { if (it.chatId == from.toString()) it.copy(chatId = to.toString()) else it }
        persist()
    }

    fun clear() {
        items = emptyList()
        persist()
    }

    /**
     * Sends what waits, oldest first. A chat stops at its first failure, so a
     * later text never lands before an earlier one; other chats carry on.
     * One drain at a time: a second call while one runs returns nothing.
     */
    suspend fun drain(deliver: suspend (OutboxItem) -> Unit): List<OutboxItem> {
        if (!draining.tryLock()) return emptyList()
        try {
            val delivered = mutableListOf<OutboxItem>()
            val stuck = mutableSetOf<String>()
            // Re-read the live queue each step: a text written mid-drain goes too,
            // and an edit made meanwhile is the text that is sent.
            while (true) {
                val next = items.firstOrNull { it.chatId !in stuck && delivered.none { d -> d.id == it.id } } ?: break
                try {
                    deliver(next)
                    delivered += next
                    items = items.filterNot { it.id == next.id }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    stuck += next.chatId
                    items = items.map { if (it.id == next.id) it.copy(attempts = it.attempts + 1) else it }
                }
                persist()
            }
            return delivered
        } finally {
            draining.unlock()
        }
    }

    private fun load(): List<OutboxItem> =
        store.get(KEY)?.let { runCatching { json.decodeFromString<List<OutboxItem>>(it) }.getOrNull() }.orEmpty()

    private fun persist() = store.set(KEY, json.encodeToString(items))

    private companion object { const val KEY = "items" }
}
