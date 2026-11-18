package com.travelneveralone.tna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/// Location in chats (TNACloud 0158) - same rules as iOS ChatLocationTests.
class ChatLocationTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    @Test fun liveStateFollowsClockAndStop() {
        val loc = LocationData(1.0, 2.0, liveUntil = now.plusSeconds(600))
        assertTrue(loc.isLive(now))
        assertFalse(loc.isLive(now.plusSeconds(601)))
        assertFalse(loc.copy(stopped = true).isLive(now))
        assertFalse(LocationData(1.0, 2.0).isLive(now))   // a pin is never live
    }

    @Test fun remaining() {
        assertEquals("ещё 42 мин", LocationText.remaining(now.plusSeconds(42 * 60 + 10), now))
        assertEquals("ещё 1 мин", LocationText.remaining(now.plusSeconds(20), now))
        assertEquals("ещё 8 ч", LocationText.remaining(now.plusSeconds(8 * 3600), now))
        assertEquals("ещё 7 ч 30 мин", LocationText.remaining(now.plusSeconds(7 * 3600 + 30 * 60), now))
    }

    @Test fun updated() {
        assertNull(LocationText.updated(null, now))
        assertEquals("обновлено только что", LocationText.updated(now.minusSeconds(20), now))
        assertEquals("обновлено 5 мин назад", LocationText.updated(now.minusSeconds(5 * 60), now))
        assertEquals("обновлено 3 ч назад", LocationText.updated(now.minusSeconds(3 * 3600), now))
    }

    @Test fun statusTitles() {
        val live = LocationData(1.0, 2.0, liveUntil = now.plusSeconds(600))
        assertEquals("Трансляция геопозиции", LocationText.status(live, now).first)
        assertEquals("Трансляция завершена", LocationText.status(live, now.plusSeconds(700)).first)
        val pin = LocationData(1.0, 2.0, accuracy = 12.4)
        assertEquals("Геопозиция", LocationText.status(pin, now).first)
        assertEquals("точность ~12 м", LocationText.status(pin, now).second)
        assertEquals("📍 Геопозиция", LocationText.label(pin))
        assertEquals("📍 Трансляция геопозиции", LocationText.label(live))
    }

    @Test fun throttleByTimeDistanceAndHeartbeat() {
        val start = GeoPoint(31.6258, -7.9891, 10.0, now)
        assertTrue(LiveThrottle.shouldSend(start, null))
        // Too soon, however far.
        assertFalse(LiveThrottle.shouldSend(GeoPoint(31.63, -7.98, 10.0, now.plusSeconds(5)), start))
        // Late enough but ~3 m away.
        assertFalse(LiveThrottle.shouldSend(GeoPoint(31.62582, -7.98912, 10.0, now.plusSeconds(15)), start))
        // Late enough and ~500 m away.
        assertTrue(LiveThrottle.shouldSend(GeoPoint(31.63, -7.985, 10.0, now.plusSeconds(15)), start))
        // Barely moving, but the heartbeat is due.
        assertTrue(LiveThrottle.shouldSend(GeoPoint(31.6258, -7.9891, 10.0, now.plusSeconds(65)), start))
    }

    @Test fun mapShowsLiveOnesPerAuthorPlusTheFocus() {
        val guide = UUID.randomUUID()
        fun msg(author: UUID?, created: Instant, loc: LocationData) = Message(
            text = "📍", isMine = false, author = "Гид", time = "12:00",
            kind = MessageKind.Location, createdAt = created, authorId = author, location = loc,
        )
        val live = LocationData(1.0, 2.0, liveUntil = now.plusSeconds(600))
        val older = msg(guide, now.minusSeconds(120), live)
        val newer = msg(guide, now.minusSeconds(60), live)
        val ended = msg(UUID.randomUUID(), now.minusSeconds(30), live.copy(stopped = true))
        val pin = msg(UUID.randomUUID(), now.minusSeconds(10), LocationData(3.0, 4.0))

        assertEquals(listOf(newer.id), chatMapPoints(listOf(older, newer, ended, pin), null, now).map { it.id })
        assertEquals(setOf(newer.id, pin.id), chatMapPoints(listOf(older, newer, ended, pin), pin, now).map { it.id }.toSet())
    }
}
