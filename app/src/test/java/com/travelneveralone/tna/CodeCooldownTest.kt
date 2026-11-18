package com.travelneveralone.tna

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Mirrors TNATests/Auth/CodeCooldownTests.swift and the wait / words parts of
/// CodeRequestErrorTests.swift: login codes are paid SMS.
class CodeCooldownTest {
    private val t0 = 1_000_000_000L
    private val phone = "+79161234567"

    @Test fun freshNumberCanGetACodeAtOnce() {
        val c = CodeCooldown()
        assertEquals(0L, c.secondsUntilNewCode(phone, t0))
        assertFalse(c.hasLiveCode(phone, t0))
    }

    @Test fun sentCodeStartsTheServerPause() {
        val c = CodeCooldown()
        c.recordSent(phone, t0)
        assertEquals(60L, c.secondsUntilNewCode(phone, t0))
        assertEquals(1L, c.secondsUntilNewCode(phone, t0 + 59_200))
        assertEquals(0L, c.secondsUntilNewCode(phone, t0 + 60_000))
        assertEquals(0L, c.secondsUntilNewCode("+79160000000", t0))
    }

    @Test fun numberSpellingDoesNotMatter() {
        val c = CodeCooldown()
        c.recordSent("+7 916 123-45-67", t0)
        assertTrue(c.hasLiveCode(phone, t0 + 10_000))
        assertEquals(50L, c.secondsUntilNewCode("79161234567", t0 + 10_000))
    }

    @Test fun codeStaysUsableForItsLifetimeOnly() {
        val c = CodeCooldown()
        c.recordSent(phone, t0)
        assertTrue(c.hasLiveCode(phone, t0 + 120_000))
        assertFalse(c.hasLiveCode(phone, t0 + CodeCooldown.CODE_LIFETIME_SECONDS * 1000))
    }

    @Test fun onlyTheLastCodeIsLive() {
        val c = CodeCooldown()
        c.recordSent(phone, t0)
        c.recordSent("+79160000000", t0 + 5_000)
        assertFalse(c.hasLiveCode(phone, t0 + 6_000))
        assertTrue(c.hasLiveCode("+79160000000", t0 + 6_000))
        assertEquals(54L, c.secondsUntilNewCode(phone, t0 + 6_000))
    }

    @Test fun usedCodeIsForgotten() {
        val c = CodeCooldown()
        c.recordSent(phone, t0)
        c.forgetLiveCode()
        assertFalse(c.hasLiveCode(phone, t0 + 1_000))
        assertEquals(59L, c.secondsUntilNewCode(phone, t0 + 1_000))
    }

    @Test fun serverRefusalSetsTheWait() {
        val c = CodeCooldown()
        c.recordRefusal(phone, 40, t0)
        assertEquals(40L, c.secondsUntilNewCode(phone, t0))
        assertFalse(c.hasLiveCode(phone, t0))
        c.recordRefusal("+79160000000", 0, t0)
        assertEquals(0L, c.secondsUntilNewCode("+79160000000", t0))
    }

    @Test fun countdownText() {
        mapOf(0L to "0:00", 5L to "0:05", 59L to "0:59", 60L to "1:00", 65L to "1:05",
              3599L to "59:59", 3600L to "1 ч", 3601L to "2 ч", -3L to "0:00")
            .forEach { (seconds, text) -> assertEquals(text, CodeCooldown.countdown(seconds)) }
    }

    // Server refusals and their wait (iOS CodeRequestError).

    @Test fun refusalCarriesTheWait() {
        val pause = codeRequestError(429, """{"msg":"Повторная отправка возможна через 40 сек"}""")
        assertEquals("Повторная отправка возможна через 40 сек", pause?.message)
        assertEquals(40L, pause?.retryAfterSec)

        val daily = codeRequestError(429, """{"error":{"http_code":429,"message":"Слишком много попыток. Новый код можно запросить через 3 ч"}}""")
        assertEquals(3 * 3600L, daily?.retryAfterSec)

        val gotrue = codeRequestError(429, """{"msg":"For security purposes, you can only request this after 27 seconds."}""")
        assertEquals(TOO_MANY_CODE_ATTEMPTS, gotrue?.message)
        assertEquals(27L, gotrue?.retryAfterSec)

        val budget = codeRequestError(429, """{"msg":"Сейчас не получается отправить код. Попробуй чуть позже"}""")
        assertNull(budget?.retryAfterSec)

        assertNull(codeRequestError(500, """{"msg":"Internal Server Error"}"""))
    }

    @Test fun waitUnits() {
        assertEquals(300L, codeRequestWait("через 5 мин"))
        assertEquals(1L, codeRequestWait("через 1 сек"))
        assertEquals(1L, codeRequestWait("after 1 second"))
        assertEquals(120L, codeRequestWait("after 2 minutes"))
        assertNull(codeRequestWait("попробуй позже"))
    }

    @Test fun wordsForAFailedRequest() {
        assertEquals("Проверь номер телефона", codeRequestFailureText(CodeRequestException("Проверь номер телефона", null)))
        assertTrue(codeRequestFailureText(IOException("offline")).startsWith("Нет связи"))
        assertTrue(codeRequestFailureText(TnaException("Сервер ответил 500")).startsWith("Не получилось"))
    }

    @Test fun wordsForACodeNotAccepted() {
        // Wrong and expired look the same from GoTrue: one sentence, a way out.
        assertTrue(codeVerifyFailureText(CodeVerifyException(403)).startsWith("Код не подошёл"))
        assertTrue(codeVerifyFailureText(CodeVerifyException(400)).startsWith("Код не подошёл"))
        assertTrue(codeVerifyFailureText(CodeVerifyException(429)).startsWith("Слишком много"))
        assertTrue(codeVerifyFailureText(IOException("timeout")).startsWith("Нет связи"))
    }
}
