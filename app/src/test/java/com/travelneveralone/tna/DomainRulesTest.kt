package com.travelneveralone.tna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DomainRulesTest {
    @Test fun pluralRussian() {
        assertEquals("тур", pluralRU(1, "тур", "тура", "туров"))
        assertEquals("тура", pluralRU(2, "тур", "тура", "туров"))
        assertEquals("туров", pluralRU(5, "тур", "тура", "туров"))
        assertEquals("туров", pluralRU(11, "тур", "тура", "туров"))
        assertEquals("тур", pluralRU(21, "тур", "тура", "туров"))
        assertEquals("туров", pluralRU(14, "тур", "тура", "туров"))
    }

    @Test fun seasonsFromDateAndLabel() {
        assertEquals("Зима", seasonName(LocalDate.of(2026, 1, 15)))
        assertEquals("Весна", seasonName(LocalDate.of(2026, 4, 2)))
        assertEquals("Лето", seasonName(LocalDate.of(2026, 8, 12)))
        assertEquals("Осень", seasonName(LocalDate.of(2026, 10, 1)))
        assertEquals("Лето", seasonFromLabel("12–22 авг"))
        assertEquals("Весна", seasonFromLabel("Сакура · 3–13 апр"))
        assertEquals("Зима", seasonFromLabel("12–18 фев"))
    }

    @Test fun tourSeasonTextSpansDepartures() {
        val tour = Tour(
            name = "Япония", tagline = "", hashtag = "", region = TourRegion.World,
            days = 10, km = 1, priceFrom = 1, difficulty = 1, maxAltitude = 1, symbol = "x",
            colors = listOf(0L),
            departures = listOf(
                Departure(label = "март", spotsLeft = 1, startsOn = LocalDate.of(2027, 3, 28)),
                Departure(label = "зима", spotsLeft = 1, startsOn = LocalDate.of(2027, 12, 1)),
            ),
            program = emptyList(), includes = emptyList(),
        )
        assertEquals("Зима · Весна", tour.seasonText)
    }

    @Test fun catalogFilterHidesUselessChips() {
        val tours = Mock.tours
        val chips = CatalogFilter.available(tours)
        assertTrue(chips.contains(CatalogFilter.All))
        assertTrue(chips.contains(CatalogFilter.Russia))
        assertTrue(chips.contains(CatalogFilter.World))
        assertEquals(1, CatalogFilter.Russia.apply(tours).count { it.name == "Камчатка" })
        assertTrue(filterTours(tours, CatalogFilter.All, "байкал").single().name == "Байкал")
    }

    @Test fun compassTopMatchIsFullPercent() {
        val answers = QuizAnswers(QuizVibe.Ice, QuizSeason.Winter, QuizEffort.Easy, QuizBudget.Low)
        val ranked = TourMatchEngine.rank(Mock.tours, answers)
        assertEquals(100, ranked.first().percent)
        assertEquals("Байкал", ranked.first().tour.name)
    }

    @Test fun campaignClearsToursThatLeftTheSale() {
        val tour = Mock.tours.first().copy(backendId = UUID.randomUUID(), salePriceFrom = 10, saleLabel = "старое")
        val list = mutableListOf(tour)
        CampaignApplication.apply(null, list)
        assertNull(list[0].salePriceFrom)
        val offer = CampaignOffer("bf", "Чёрная пятница", "−20%", "black_friday", "2026-11-30",
            mapOf(tour.backendId!! to CampaignSale(100_000, "ЧП")))
        CampaignApplication.apply(offer, list)
        assertEquals(100_000, list[0].salePriceFrom)
        assertTrue(list[0].hasSale)
    }

    @Test fun bonusCopyAndBookingNote() {
        val line = BonusLine(UUID.randomUUID(), "referral_first", 5000, 2000, "available", "2026-12-01", "Камчатка")
        assertTrue(line.text.contains("за первую поездку друга"))
        assertTrue(line.text.contains("до 01.12.26"))
        assertEquals(
            "Скидка по коду 1 000 ₽, бонусами закрыто 500 ₽",
            BonusCopy.bookingNote(1000, 500),
        )
        assertNull(BonusCopy.bookingNote(0, 0))
    }

    @Test fun rolesAndDirectMessages() {
        assertTrue(UserRole.Client.canBookAndEarn)
        assertFalse(UserRole.Guide.canBookAndEarn)
        assertTrue(UserRole.Manager.isOpsOnly)
        assertFalse(UserRole.Manager.hasTravelProfile)
        assertFalse(UserRole.Client.mayStartDirectMessage(UserRole.Admin))
        assertTrue(UserRole.Client.mayStartDirectMessage(UserRole.Guide))
        assertFalse(UserRole.Partner.mayStartDirectMessage(UserRole.Client))
        assertEquals(UserRole.Client, UserRole.fromRaw(""))
        assertEquals(UserRole.Client, UserRole.fromRaw("nope"))
        val server = DirectMessagePolicy(listOf(UserRole.Manager))
        assertTrue(server.allows(UserRole.Client, UserRole.Manager))
        assertFalse(server.allows(UserRole.Client, UserRole.Guide))
        assertTrue(DirectMessagePolicy(null).allows(UserRole.Client, UserRole.Guide))
    }

    @Test fun phoneAndBookingForm() {
        assertEquals("79991234567", PhoneNumber.digits("8 (999) 123-45-67"))
        assertEquals("+79991234567", PhoneNumber.e164("9991234567"))
        assertEquals("+7 999 000-00-06", PhoneNumber.pretty("+7 999 000 00 06"))
        val tour = Mock.tours.first()
        val form = BookingForm(tour, tour.departures.first(), "А", "+79990000006")
        assertFalse(form.canSubmit)
        form.name = "Анна"
        assertTrue(form.canSubmit)
        val sold = tour.departures.first().copy(spotsLeft = 0)
        val wait = BookingForm(tour, sold, "Анна", "+79990000006")
        assertTrue(wait.isWaitlist)
        assertTrue(wait.seatLimitNote!!.contains("не осталось"))
    }

    @Test fun albumsAndUnread() {
        val album = UUID.randomUUID()
        val messages = listOf(
            Message(text = "a", isMine = true, author = "я", time = "1", kind = MessageKind.Image, albumId = album),
            Message(text = "b", isMine = true, author = "я", time = "1", kind = MessageKind.Image, albumId = album),
            Message(text = "c", isMine = false, author = "Аня", time = "2"),
        )
        val groups = messages.albumGrouped()
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].size)
        val read = Instant.parse("2026-01-01T00:00:00Z")
        val newer = Instant.parse("2026-01-02T00:00:00Z")
        assertEquals(1, ChatUnread.flag(newer, newer, false, read))
        assertEquals(0, ChatUnread.flag(read, newer, true, read))
    }

    @Test fun travelerLevelMatchesIosLadder() {
        assertEquals(TravelerLevel.Novice, TravelerLevel.forTours(0))
        assertEquals(TravelerLevel.Traveler, TravelerLevel.forTours(1))
        assertEquals(TravelerLevel.Pioneer, TravelerLevel.forTours(3))
        assertEquals(TravelerLevel.Legend, TravelerLevel.forTours(7))
        assertEquals(1.0, TravelerLevel.Legend.progress(9), 0.001)
        assertEquals("Анна К.", PassportCopy.shortName("Анна Крылова", false))
        assertEquals("Анна Крылова", PassportCopy.shortName("Анна Крылова", true))
        assertEquals("с TNA с 2024", PassportCopy.memberSince(2024))
        assertEquals("", PassportCopy.memberSince(1990))
        assertEquals(-8.0, PassportCopy.stampAngle(0), 0.001)
        assertEquals(5.0, PassportCopy.stampAngle(1), 0.001)
    }

    @Test fun referralResultCopy() {
        assertEquals(ReferralResult.Ok, ReferralResult.fromRaw("ok"))
        assertTrue(ReferralResult.Existing.userMessage.contains("уже ездил"))
    }
}
