package com.travelneveralone.tna

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Mirrors TNATests/Auth/AppLockTests.swift.
class AppLockTest {
    private class MapFlags : FlagStore {
        val values = mutableMapOf<String, Boolean>()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: Boolean) { values[key] = value }
        override fun clear() = values.clear()
    }

    private var clock = 1_000_000L

    private fun lock(
        flags: FlagStore,
        bio: MockBiometrics = MockBiometrics(),
        registered: Boolean = true,
    ) = AppLock(flags, bio, isRegistered = { registered }, now = { clock })

    @Test fun enablingRequiresSuccessfulBiometrics() = runBlocking {
        val bio = MockBiometrics(succeeds = false)
        val l = lock(MapFlags(), bio)
        assertFalse(l.setEnabled(true))
        assertFalse(l.isEnabled)
        assertEquals(1, bio.prompts)
    }

    @Test fun enabledSurvivesRelaunchAndLocksColdStart() = runBlocking {
        val flags = MapFlags()
        val first = lock(flags)
        first.setEnabled(true)
        assertFalse(first.isLocked)
        val relaunched = lock(flags)
        assertTrue(relaunched.isEnabled)
        assertTrue(relaunched.isLocked)
    }

    @Test fun notOfferedWithoutBiometricsOrAccount() {
        assertFalse(lock(MapFlags(), MockBiometrics(kind = BiometryKind.None)).isOfferable)
        assertFalse(lock(MapFlags(), registered = false).isOfferable)
    }

    @Test fun anonymousUserIsNeverLocked() = runBlocking {
        val flags = MapFlags()
        lock(flags).setEnabled(true)
        assertFalse(lock(flags, registered = false).isLocked)
    }

    @Test fun relocksOnlyAfterGracePeriod() = runBlocking {
        for ((away, locks) in listOf(60_000L to false, AppLock.GRACE_MS to true, 3_600_000L to true)) {
            val flags = MapFlags()
            lock(flags).setEnabled(true)
            val l = lock(flags)
            l.unlock()
            assertFalse(l.isLocked)
            l.didEnterBackground()
            clock += away
            l.didBecomeActive()
            assertEquals("away $away ms", locks, l.isLocked)
        }
    }

    @Test fun activeWithoutBackgroundDoesNotRelock() = runBlocking {
        val flags = MapFlags()
        lock(flags).setEnabled(true)
        val l = lock(flags)
        l.unlock()
        clock += 3_600_000L
        l.didBecomeActive()
        assertFalse(l.isLocked)
    }

    @Test fun failedBiometricsKeepsLocked() = runBlocking {
        val flags = MapFlags()
        lock(flags).setEnabled(true)
        val l = lock(flags, MockBiometrics(succeeds = false))
        l.unlock()
        assertTrue(l.isLocked)
    }

    @Test fun removedPinTurnsLockOff() = runBlocking {
        val flags = MapFlags()
        lock(flags).setEnabled(true)
        val l = lock(flags, MockBiometrics(canAuthenticate = false))
        l.unlock()
        assertFalse(l.isLocked)
        assertFalse(l.isEnabled)
        assertFalse(lock(flags).isEnabled)
    }

    @Test fun signOutDropsTheLock() = runBlocking {
        val flags = MapFlags()
        val l = lock(flags)
        l.setEnabled(true)
        l.reset()
        assertFalse(l.isEnabled)
        assertFalse(lock(flags).isEnabled)
    }

    // Sign-in after a sign-out

    @Test fun quickSignInTurnsTheLockBackOnWithOnePrompt() = runBlocking {
        val flags = MapFlags()
        val bio = MockBiometrics()
        val l = lock(flags, bio)
        assertTrue(l.confirmQuickSignIn())
        l.restoreAfterQuickSignIn()
        assertEquals(1, bio.prompts)          // no second prompt for the lock
        assertTrue(l.isEnabled)
        assertFalse(l.isLocked)               // the app stays open
        assertFalse(l.shouldOffer)
        assertTrue(lock(flags).isEnabled)     // persisted
    }

    @Test fun quickSignInNeedsBiometrics() = runBlocking {
        assertFalse(lock(MapFlags(), MockBiometrics(succeeds = false)).confirmQuickSignIn())
        val none = MockBiometrics(kind = BiometryKind.None)
        val l = lock(MapFlags(), none)
        assertFalse(l.canConfirmQuickSignIn)
        assertFalse(l.confirmQuickSignIn())
        assertEquals(0, none.prompts)
    }

    @Test fun offeredOncePerAccount() {
        val flags = MapFlags()
        val l = lock(flags)
        assertTrue(l.shouldOffer)
        l.markOffered()
        assertFalse(l.shouldOffer)
        assertFalse(lock(flags).shouldOffer)
    }
}

/// What refreshing the session a sign-out kept means (iOS AuthSession.quickSignIn).
class QuickSignInOutcomeTest {
    @Test fun successSignsIn() {
        assertEquals(QuickSignInResult.SignedIn, quickSignInOutcome(200))
    }

    @Test fun rejectedTokenNeedsAnSms() {
        assertEquals(QuickSignInResult.Expired, quickSignInOutcome(400))
        assertEquals(QuickSignInResult.Expired, quickSignInOutcome(401))
    }

    @Test fun flakyServerKeepsTheSessionForARetry() {
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(null))   // no network
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(408))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(429))
        assertEquals(QuickSignInResult.Offline, quickSignInOutcome(503))
    }
}

/// Mirrors TNATests/Auth/CodeRequestErrorTests.swift.
class CodeRequestMessageTest {
    @Test fun hookSentenceIsShownAsIs() {
        val body = """{"code":429,"error_code":"hook_error","msg":"Код уже отправлен. Новый можно запросить через 40 сек"}"""
        assertEquals("Код уже отправлен. Новый можно запросить через 40 сек", codeRequestMessage(429, body))
    }

    @Test fun nestedHookErrorIsRead() {
        val body = """{"error":{"http_code":429,"message":"Слишком много попыток. Новый код можно запросить через 2 ч"}}"""
        assertEquals("Слишком много попыток. Новый код можно запросить через 2 ч", codeRequestMessage(429, body))
    }

    @Test fun englishRateLimitGetsRussianLine() {
        val body = """{"code":429,"msg":"For security purposes, you can only request this after 52 seconds."}"""
        assertEquals(TOO_MANY_CODE_ATTEMPTS, codeRequestMessage(429, body))
        assertEquals(TOO_MANY_CODE_ATTEMPTS, codeRequestMessage(429, "not json"))
    }

    @Test fun otherFailuresStayGeneric() {
        assertEquals(null, codeRequestMessage(500, """{"msg":"Internal Server Error"}"""))
        assertEquals(null, codeRequestMessage(422, ""))
    }
}
