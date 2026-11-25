package com.travelneveralone.tna

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-level helpers from Net.kt and Repository.kt that have no Android in them:
 * Supabase timestamps, the biometric sign-in outcome and the cache gate
 * (iOS SupabaseDateTests, AuthSessionTokenTests).
 */
class NetRulesTest {
    @Test fun parsesEveryTimestampShapeSupabaseSends() {
        val expected = Instant.parse("2026-11-21T10:15:30Z")
        assertEquals(expected, parseInstant("2026-11-21T10:15:30Z"))
        assertEquals(expected, parseInstant("2026-11-21T10:15:30+00:00"))
        assertEquals(expected, parseInstant("2026-11-21 10:15:30"))
        assertEquals(expected, parseInstant("2026-11-21T10:15:30"))
        assertEquals(Instant.parse("2026-11-21T10:15:30.123456Z"), parseInstant("2026-11-21 10:15:30.123456+00:00"))
        assertNull(parseInstant(null))
        assertNull(parseInstant(" "))
        assertNull(parseInstant("вчера"))
        assertEquals("", displayDay("garbage"))
        assertEquals("", displayTime(null))
        assertEquals(5, displayTime("2026-11-21T10:15:30Z").length)
    }

    @Test fun newsWindowEndIsExclusive() {
        val end = "2026-11-21T10:00:00Z"
        assertFalse(newsWindowOver(end, Instant.parse("2026-11-21T09:59:59Z")))
        assertTrue(newsWindowOver(end, Instant.parse("2026-11-21T10:00:00Z")))
    }

    @Test fun quickSignInOnlyExpiresWhenTheServerRefusesTheToken() {
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(null))
        assertEquals(QuickSignInResult.SignedIn, quickSignInOutcome(200))
        assertEquals(QuickSignInResult.Expired, quickSignInOutcome(400))
        assertEquals(QuickSignInResult.Expired, quickSignInOutcome(401))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(408))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(429))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(500))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(302))
    }

    @Test fun onlyReadsAndMarkedRpcsAreCached() {
        assertTrue(postgrestCaches("GET", "/rest/v1/tours", null))
        assertTrue(postgrestCaches("POST", "/rest/v1/rpc/catalog_overview", 60))
        assertFalse(postgrestCaches("POST", "/rest/v1/rpc/join_waitlist", null))
        assertFalse(postgrestCaches("POST", "/rest/v1/bookings", 60))
        assertFalse(postgrestCaches("PATCH", "/rest/v1/profiles", null))
    }

    @Test fun monthTitleIsCapitalisedNominative() {
        assertEquals("Май 2027", monthTitle(LocalDate.of(2027, 5, 14)))
        assertEquals("Январь 2026", monthTitle(LocalDate.of(2026, 1, 1)))
    }

    // TNA-122: pull-to-refresh must reach the server, but stay on the cached
    // path (ttl 0, not null) so offline still shows the last passport.
    @Test fun freshPassportSkipsCacheButKeepsOfflineCopy() {
        assertEquals(300, passportTtl(fresh = false))
        assertEquals(0, passportTtl(fresh = true))
        assertTrue(postgrestCaches("POST", "rest/v1/rpc/my_passport", passportTtl(fresh = true)))
    }
}
