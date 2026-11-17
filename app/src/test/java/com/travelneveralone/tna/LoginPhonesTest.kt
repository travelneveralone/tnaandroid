package com.travelneveralone.tna

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Mirrors TNATests/Profile/LoginPhonesViewModelTests.swift.
class LoginPhonesTest {
    private val anya = LoginPhone("+79990000001", true)
    private val second = LoginPhone("+31686432839", false)

    /// Records calls; answers with scripted outcomes. listed == null = offline.
    private class Scripted(
        var listed: List<LoginPhone>? = null,
        var sendOutcome: LoginPhoneOutcome = LoginPhoneOutcome.Ok(null),
    ) : LoginPhonesBackend {
        val sent = mutableListOf<String>()
        val confirmed = mutableListOf<String>()
        val removed = mutableListOf<String>()
        override suspend fun list() = listed
        override suspend fun sendCode(phone: String): LoginPhoneOutcome { sent += phone; return sendOutcome }
        override suspend fun confirm(code: String): LoginPhoneOutcome { confirmed += code; return LoginPhoneOutcome.Ok(listed) }
        override suspend fun remove(phone: String): LoginPhoneOutcome { removed += phone; return LoginPhoneOutcome.Ok(listed) }
    }

    @Test fun loadsTheList() = runBlocking {
        val flow = LoginPhonesFlow(MockLoginPhones(mutableListOf(anya, second)))
        flow.load()
        assertEquals(listOf(anya, second), flow.phones)
        assertTrue(flow.loaded)
        assertFalse(flow.loadFailed)
    }

    @Test fun failedLoadWithNothingToShow() = runBlocking {
        val flow = LoginPhonesFlow(Scripted())
        flow.load()
        assertTrue(flow.loadFailed)
    }

    @Test fun attachByCode() = runBlocking {
        val flow = LoginPhonesFlow(MockLoginPhones(mutableListOf(anya)))
        flow.load()
        flow.startAdding()
        flow.sendCode("+79160000002")
        assertEquals(LoginPhonesFlow.Step.Code("+79160000002"), flow.step)
        flow.code = "123456"
        flow.confirm()
        assertEquals(LoginPhonesFlow.Step.Numbers, flow.step)
        assertEquals(listOf("+79990000001", "+79160000002"), flow.phones.map { it.phone })
        assertNotNull(flow.notice)
        assertNull(flow.error)
    }

    @Test fun ownNumberIsNotSentAgain() = runBlocking {
        val spy = Scripted(listed = listOf(anya))
        val flow = LoginPhonesFlow(spy)
        flow.load()
        flow.startAdding()
        flow.sendCode("8 999 000 00 01")
        assertTrue(spy.sent.isEmpty())
        assertNotNull(flow.error)
        assertEquals(LoginPhonesFlow.Step.Phone, flow.step)
    }

    @Test fun refusalTextIsShownAsIs() = runBlocking {
        val text = "Этот номер привязан к другому аккаунту"
        val flow = LoginPhonesFlow(Scripted(listed = listOf(anya), sendOutcome = LoginPhoneOutcome.Refused(text)))
        flow.load()
        flow.startAdding()
        flow.sendCode("+79160000002")
        assertEquals(text, flow.error)
        assertEquals(LoginPhonesFlow.Step.Phone, flow.step)
    }

    @Test fun wrongCodeStaysOnCodeStep() = runBlocking {
        val flow = LoginPhonesFlow(MockLoginPhones(mutableListOf(anya)))
        flow.startAdding()
        flow.sendCode("+79160000002")
        flow.code = "000000"
        flow.confirm()
        assertEquals(LoginPhonesFlow.Step.Code("+79160000002"), flow.step)
        assertEquals("Неверный код", flow.error)
    }

    @Test fun shortCodeIsNotSent() = runBlocking {
        val spy = Scripted(listed = listOf(anya))
        val flow = LoginPhonesFlow(spy)
        flow.startAdding()
        flow.sendCode("+79160000002")
        flow.code = "123"
        assertFalse(flow.codeIsComplete)
        flow.confirm()
        assertTrue(spy.confirmed.isEmpty())
        assertNotNull(flow.error)
    }

    @Test fun lastNumberCannotBeRemoved() = runBlocking {
        val spy = Scripted(listed = listOf(anya))
        val flow = LoginPhonesFlow(spy)
        flow.load()
        assertFalse(flow.canRemove)
        flow.remove(anya)
        assertTrue(spy.removed.isEmpty())
    }

    @Test fun removingThePrimaryUpdatesTheProfile() = runBlocking {
        val reported = mutableListOf<String>()
        val flow = LoginPhonesFlow(MockLoginPhones(mutableListOf(anya, second))) { reported += it }
        flow.load()
        flow.remove(anya)
        assertEquals(listOf(LoginPhone("+31686432839", true)), flow.phones)
        assertEquals(listOf("+31686432839"), reported)
    }

    @Test fun removingASecondNumberKeepsThePrimary() = runBlocking {
        val reported = mutableListOf<String>()
        val flow = LoginPhonesFlow(MockLoginPhones(mutableListOf(anya, second))) { reported += it }
        flow.load()
        flow.remove(second)
        assertEquals(listOf(anya), flow.phones)
        assertTrue(reported.isEmpty())
    }

    @Test fun capOnNumbers() = runBlocking {
        val five = (1..5).map { LoginPhone("+7999000000$it", it == 1) }.toMutableList()
        val flow = LoginPhonesFlow(MockLoginPhones(five))
        flow.load()
        assertFalse(flow.canAddMore)
    }

    // MARK: Wire

    @Test fun wireOutcome() {
        assertEquals(
            LoginPhoneOutcome.Ok(listOf(anya)),
            LoginPhonesWire.outcome("""{"ok":true,"phones":[{"phone":"+79990000001","primary":true,"last_login_at":null}]}"""),
        )
        assertEquals(LoginPhoneOutcome.Ok(null), LoginPhonesWire.outcome("""{"ok":true}"""))
        assertEquals(LoginPhoneOutcome.Refused("Неверный код"),
            LoginPhonesWire.outcome("""{"error":"wrong_code","message":"Неверный код"}"""))
        assertEquals(LoginPhoneOutcome.Refused(LoginPhonesWire.GENERIC), LoginPhonesWire.outcome("not json"))
        assertEquals(LoginPhoneOutcome.Refused(LoginPhonesWire.OFFLINE), LoginPhonesWire.outcome(null))
    }

    @Test fun wireList() {
        assertEquals(listOf(anya, second), LoginPhonesWire.list(
            """{"phones":[{"phone":"+79990000001","primary":true},{"phone":"+31686432839","primary":false}]}"""))
        assertNull(LoginPhonesWire.list(null))
        assertNull(LoginPhonesWire.list("""{"error":"unauthorized"}"""))
    }
}
