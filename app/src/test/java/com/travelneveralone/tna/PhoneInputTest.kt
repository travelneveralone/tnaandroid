package com.travelneveralone.tna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirrors TNAiOS TNATests/Auth/PhoneInputTests.swift + the new PhoneNumberTests cases.
class PhoneInputTest {

    private fun c(iso: String) = PhoneCountry.withIso(iso)!!

    // Table

    @Test fun tableIsSane() {
        assertTrue(PHONE_COUNTRIES.size > 200)
        assertEquals(PHONE_COUNTRIES.size, PHONE_COUNTRIES.map { it.iso }.toSet().size)
        for (x in PHONE_COUNTRIES) {
            assertTrue(x.iso, x.lengths.first >= 4 && x.lengths.last <= 14)
            assertTrue(x.iso, x.example.length in x.lengths)
        }
        for ((code, list) in PHONE_COUNTRIES.groupBy { it.dialCode }) {
            assertEquals("+$code", 1, list.count { it.isMainForCode })
        }
    }

    @Test fun flagNameAndPlaceholder() {
        assertEquals("🇷🇺", c("RU").flag)
        assertEquals("Россия", c("RU").name)
        assertEquals("+7", c("RU").dialCodeDisplay)
        assertEquals("912 345-67-89", c("RU").placeholder)
        assertEquals("6 12345678", c("NL").placeholder)
    }

    @Test fun preferredCountryFollowsRegion() {
        assertEquals("NL", PhoneCountry.preferred("NL").iso)
        assertEquals("RU", PhoneCountry.preferred("ru").iso)
        assertEquals("RU", PhoneCountry.preferred(null).iso)
        assertEquals("RU", PhoneCountry.preferred("ZZ").iso)
    }

    @Test fun popularPutsLocalRegionFirst() {
        assertEquals("NL", PhoneCountry.popular("NL").first().iso)
        assertEquals(1, PhoneCountry.popular("RU").count { it.iso == "RU" })
    }

    // Formatting

    @Test fun maskNeverLeavesTrailingSeparators() {
        val mask = c("RU").mask
        assertEquals("", PhoneInput.format("", mask))
        assertEquals("912", PhoneInput.format("912", mask))
        assertEquals("912 3", PhoneInput.format("9123", mask))
        assertEquals("912 345-6", PhoneInput.format("9123456", mask))
        assertEquals("912 345-67-89", PhoneInput.format("9123456789", mask))
        assertEquals("6 1234567890", PhoneInput.format("61234567890", c("NL").mask))
    }

    // Typing

    @Test fun russianInput() {
        for (typed in listOf("9161234567", "89161234567", "79161234567", "91612345678999")) {
            val edit = PhoneInput.apply(typed, c("RU"))
            assertEquals(typed, "RU", edit.country.iso)
            assertEquals(typed, "916 123-45-67", edit.display)
            assertTrue(typed, PhoneInput.isComplete(edit.national, edit.country))
        }
    }

    @Test fun loneTrunkDigitStaysUntilTheNextOne() {
        assertEquals("8", PhoneInput.apply("8", c("RU")).display)
        val next = PhoneInput.apply("89", c("RU"))
        assertEquals("9", next.display)
        assertEquals(1, next.droppedLeading)
    }

    @Test fun dutchLocalFormatDropsTheZero() {
        val edit = PhoneInput.apply("06 86432839", c("NL"))
        assertEquals("686432839", edit.national)
        assertEquals("6 86432839", edit.display)
    }

    @Test fun pastedInternationalNumberPicksCountry() {
        val cases = listOf(
            listOf("+31 6 86432839", "RU", "NL", "686432839"),
            listOf("+7 (916) 123-45-67", "NL", "RU", "9161234567"),
            listOf("+7 701 123 45 67", "RU", "KZ", "7011234567"),
            listOf("+7 916 123 45 67", "KZ", "RU", "9161234567"),
            listOf("+1 506 234 5678", "CA", "CA", "5062345678"),
            listOf("+1 201 555 0123", "RU", "US", "2015550123"),
            listOf("+81 90 1234 5678", "RU", "JP", "9012345678"),
            listOf("+375 29 123-45-67", "RU", "BY", "291234567"),
        )
        for ((text, from, iso, national) in cases) {
            val edit = PhoneInput.apply(text, c(from))
            assertEquals(text, iso, edit.country.iso)
            assertEquals(text, national, edit.national)
            assertNull(text, edit.pendingCode)
        }
    }

    @Test fun callingCodeTypedByHandWaitsUntilComplete() {
        assertEquals("+", PhoneInput.apply("+", c("RU")).display)
        assertEquals("3", PhoneInput.apply("+3", c("RU")).pendingCode)
        assertEquals("+35", PhoneInput.apply("+35", c("RU")).display)
        val done = PhoneInput.apply("+357", c("RU"))
        assertEquals("CY", done.country.iso)
        assertEquals("", done.display)
        assertNull(done.pendingCode)
    }

    @Test fun e164AndClamp() {
        assertEquals("+79161234567", PhoneInput.e164("9161234567", c("RU")))
        assertEquals("", PhoneInput.e164("", c("RU")))
        assertEquals("91612345", PhoneInput.clamp("9161234567", c("AM")))
        assertFalse(PhoneInput.isComplete("916123456", c("RU")))
    }

    // Caret

    @Test fun backspaceOnSeparatorDeletesTheDigitBeforeIt() {
        // "912 345-67-89" minus the first '-' (caret lands where it was).
        val (raw, digits) = PhoneInput.replace("912 345-67-89", "912 34567-89", 7)
        assertEquals("912 3467-89", raw)
        assertEquals(5, digits)
        val edit = PhoneInput.apply(raw, c("RU"))
        assertEquals("912 346-78-9", edit.display)
        assertEquals(6, PhoneInput.caretOffset(digits, edit.display))
    }

    @Test fun insertingInTheMiddleKeepsCaretAfterTheNewDigit() {
        val (raw, digits) = PhoneInput.replace("912 345", "912 0345", 5)
        val edit = PhoneInput.apply(raw, c("RU"))
        assertEquals("912 034-5", edit.display)
        assertEquals(5, PhoneInput.caretOffset(digits, edit.display))
    }

    @Test fun caretOffsetCountsDigitsOnly() {
        assertEquals(0, PhoneInput.caretOffset(0, "912 345-67"))
        assertEquals(3, PhoneInput.caretOffset(3, "912 345-67"))
        assertEquals(5, PhoneInput.caretOffset(4, "912 345-67"))
        assertEquals(10, PhoneInput.caretOffset(99, "912 345-67"))
    }

    // Seeding from stored numbers

    @Test fun parseStoredNumbers() {
        val cases = listOf(
            listOf("+7 999 000 00 06", "NL", "RU", "9990000006"),
            listOf("8 916 123-45-67", "RU", "RU", "9161234567"),
            listOf("06 86432839", "NL", "NL", "686432839"),
            listOf("79161234567", "NL", "RU", "9161234567"),
            listOf("916 123 45 67", "NL", "RU", "9161234567"),
            listOf("31686432839", "RU", "NL", "686432839"),
        )
        for ((raw, fallback, iso, national) in cases) {
            val (country, nat) = PhoneInput.parse(raw, c(fallback))
            assertEquals(raw, iso, country.iso)
            assertEquals(raw, national, nat)
        }
    }

    // Search

    @Test fun searchByNameCodeAndEnglish() {
        assertTrue(PhoneCountry.search("нидер").any { it.iso == "NL" })
        assertTrue(PhoneCountry.search("Netherlands").any { it.iso == "NL" })
        assertTrue(PhoneCountry.search("nl").any { it.iso == "NL" })
        assertEquals("NL", PhoneCountry.search("+31").first().iso)
        assertEquals(listOf("KZ", "RU"), PhoneCountry.search("7").take(2).map { it.iso }.sorted())
        assertEquals(PHONE_COUNTRIES.size, PhoneCountry.search("").size)
        assertTrue(PhoneCountry.search("абракадабра").isEmpty())
    }

    // PhoneNumber

    @Test fun plusNumbersAreNotRussified() {
        assertEquals("819012345678", PhoneNumber.digits("+81 90 1234 5678"))
        assertEquals("+3725123456", PhoneNumber.e164("+372 5123 456"))
        assertEquals("79990000000", PhoneNumber.digits("+7 999 000 00 00"))
        assertEquals("79990000000", PhoneNumber.digits("8 999 000 00 00"))
    }

    @Test fun prettyUsesEachCountrysFormat() {
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("89990000000"))
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("9990000000"))
        assertEquals("+31 6 86432839", PhoneNumber.pretty("+31686432839"))
        assertEquals("+7 701 123-45-67", PhoneNumber.pretty("+77011234567"))
        assertEquals("+1 201 555 0123", PhoneNumber.pretty("+12015550123"))
        assertEquals("", PhoneNumber.pretty(""))
    }
}
