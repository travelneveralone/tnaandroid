package com.travelneveralone.tna

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Force update (TNA-114) - iOS AppVersionGateTests: the backend decides, offline
 *  never blocks, a 426 blocks at once, «not now» holds until a newer build. */
class AppVersionTest {
    private class MemStore : StringStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun set(key: String, value: String) { map[key] = value }
    }

    private class Stub(vararg answers: AppVersionVerdict?) : AppVersionChecking {
        val queue = answers.toMutableList()
        var calls = 0
        override suspend fun check(): AppVersionVerdict? { calls++; return if (queue.isEmpty()) null else queue.removeAt(0) }
    }

    @Test fun parsesTheServerAnswerAndToleratesUnknownStatus() {
        val v = AppVersionVerdict.parse("""{"status":"soft","on_trip":true,"store_url":"https://play.google.com/x","message":"m","recommended_build":"261122.1"}""")
        assertEquals(AppVersionVerdict(AppVersionStatus.Soft, true, "https://play.google.com/x", "m", "261122.1"), v)
        assertEquals(AppVersionStatus.Ok, AppVersionVerdict.parse("""{"status":"maintenance"}""")?.status)
        assertNull(AppVersionVerdict.parse("not json"))
    }

    @Test fun forceBlocksAndOfflineKeepsWhatWeHad() = runBlocking {
        val gate = AppVersionGate(Stub(AppVersionVerdict(AppVersionStatus.Force, storeUrl = "https://x"), null), MemStore())
        assertFalse(gate.isBlocked)
        gate.refresh(force = true)
        assertTrue(gate.isBlocked)
        gate.refresh(force = true)
        assertTrue(gate.isBlocked)
    }

    @Test fun offlineOnLaunchNeverBlocks() = runBlocking {
        val gate = AppVersionGate(Stub(null), MemStore())
        gate.refresh(force = true)
        assertFalse(gate.isBlocked)
        assertFalse(gate.showsBanner)
    }

    @Test fun upgradeRequiredBlocksEvenWithoutAnAnswer() = runBlocking {
        val gate = AppVersionGate(Stub(null), MemStore())
        gate.upgradeRequired()
        assertTrue(gate.isBlocked)
    }

    @Test fun upgradeRequiredTrustsTheServerWhenItAnswers() = runBlocking {
        val gate = AppVersionGate(Stub(AppVersionVerdict(AppVersionStatus.Soft, onTrip = true)), MemStore())
        gate.upgradeRequired()
        assertFalse(gate.isBlocked)
        assertTrue(gate.showsBanner)
    }

    @Test fun foregroundChecksAreThrottled() = runBlocking {
        var clock = 1_000_000L
        val stub = Stub(AppVersionVerdict(AppVersionStatus.Ok), AppVersionVerdict(AppVersionStatus.Ok))
        val gate = AppVersionGate(stub, MemStore(), now = { clock })
        gate.refresh()
        gate.refresh()
        assertEquals(1, stub.calls)
        clock += AppVersionGate.MIN_INTERVAL_MS + 1
        gate.refresh()
        assertEquals(2, stub.calls)
    }

    @Test fun dismissedBannerComesBackOnlyForANewerBuild() = runBlocking {
        val soft = { b: String -> AppVersionVerdict(AppVersionStatus.Soft, recommendedBuild = b) }
        val store = MemStore()
        val gate = AppVersionGate(Stub(soft("261122.1"), soft("261122.1"), soft("261201.1")), store)
        gate.refresh(force = true)
        assertTrue(gate.showsBanner)
        gate.dismissBanner()
        assertFalse(gate.showsBanner)
        gate.refresh(force = true)
        assertFalse(gate.showsBanner)
        gate.refresh(force = true)
        assertTrue(gate.showsBanner)
    }

    @Test fun storeLinkMustBeHttps() = runBlocking {
        val gate = AppVersionGate(Stub(AppVersionVerdict(AppVersionStatus.Force, storeUrl = "javascript:x")), MemStore())
        gate.refresh(force = true)
        assertNull(gate.storeUrl)
    }

    @Test fun everyRequestNamesItsBuild() {
        assertEquals("android", AppBuild.headers["x-app-platform"])
        assertTrue(AppBuild.headers.containsKey("x-app-build"))
    }
}
