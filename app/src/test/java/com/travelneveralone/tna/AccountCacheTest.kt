package com.travelneveralone.tna

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Testers switch accounts on one phone all day: one person's cached role,
/// trips or chats must never answer for another (TNA-75, TNA-81, TNA-87),
/// and a write RPC must never "succeed" with an old cached reply.
class AccountCacheTest {
    private val url = "https://example.supabase.co/rest/v1/rpc/my_role"

    @Test fun sameRequestOfTwoAccountsHasTwoKeys() {
        assertNotEquals(
            postgrestCacheKey("POST", url, "{}", "user-a"),
            postgrestCacheKey("POST", url, "{}", "user-b"),
        )
    }

    @Test fun sameAccountKeepsItsKey() {
        assertEquals(
            postgrestCacheKey("POST", url, "{}", "user-a"),
            postgrestCacheKey("POST", url, "{}", "user-a"),
        )
    }

    @Test fun readsAreCachedWritesAreNot() {
        assertTrue(postgrestCaches("GET", "rest/v1/tours", null))
        assertTrue(postgrestCaches("POST", "rest/v1/rpc/my_role", 0))
        assertFalse(postgrestCaches("POST", "rest/v1/rpc/join_waitlist", null))
        assertFalse(postgrestCaches("POST", "rest/v1/bookings", null))
    }

    @Test fun signOutClearsTheDiskCache() {
        val cache = DiskCache(Files.createTempDirectory("tna-cache").toFile())
        cache.write("k", "previous person")
        cache.clear()
        assertNull(cache.readStale("k"))
    }

    @Test fun oldBaseCustomerIsWelcomedBack() {
        assertEquals(ReturningCustomer("Галина (тест 3)", 3), ReturningCustomer.from(" Галина (тест 3) ", 3))
        assertNull(ReturningCustomer.from("Галина", 0))
        assertNull(ReturningCustomer.from("Галина", null))
        assertNull(ReturningCustomer.from("  ", 2))
    }
}
