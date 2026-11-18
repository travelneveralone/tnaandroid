package com.travelneveralone.tna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/// Removing a traveller from a trip chat (0157) - iOS ChatModerationTests.
class ChatModerationTest {
    private fun person(role: String, id: UUID = UUID.randomUUID(), blocked: Boolean = false) =
        ChatParticipant(id, "Имя", "", role, isAdmin = false, muted = false, isBlocked = blocked)

    @Test fun teamCanBlockOnlyTravellers() {
        val me = UUID.randomUUID()
        assertTrue(ChatModeration.canBlock(person("client"), me, canBlockHere = true))
        for (role in listOf("guide", "curator", "manager", "admin", "partner")) {
            assertFalse(role, ChatModeration.canBlock(person(role), me, canBlockHere = true))
        }
    }

    @Test fun noActionWithoutTheRight() {
        assertFalse(ChatModeration.canBlock(person("client"), UUID.randomUUID(), canBlockHere = false))
        assertFalse(ChatModeration.canUnblock(person("client", blocked = true), canBlockHere = false))
    }

    @Test fun neverYourselfAndNotTwice() {
        val me = UUID.randomUUID()
        assertFalse(ChatModeration.canBlock(person("client", id = me), me, canBlockHere = true))
        assertFalse(ChatModeration.canBlock(person("client", blocked = true), me, canBlockHere = true))
        assertTrue(ChatModeration.canUnblock(person("client", blocked = true), canBlockHere = true))
        assertFalse(ChatModeration.canUnblock(person("client"), canBlockHere = true))
    }
}
