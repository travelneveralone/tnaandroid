package com.travelneveralone.tna

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain domain rules from Models.kt, ported case for case from the iOS twins
 * (DepartureDatesTests, PhoneNumberTests, ReturningCustomerTests, UserRoleTests,
 * DirectMessagePolicyTests, ReferralTests, DisplayLogicTests). If one of these
 * fails, the two apps show different things for the same data.
 */
class ModelRulesTest {
    private fun day(s: String) = LocalDate.parse(s)

    // MARK: - DepartureDates (iOS DepartureDatesTests, SQL departure_label 0168)

    @Test fun departureLabelSameMonth() {
        assertEquals("21–23 фев 2027", DepartureDates.label(day("2027-02-21"), day("2027-02-23")))
    }

    @Test fun departureLabelSpanningMonths() {
        assertEquals("28 фев – 3 мар 2027", DepartureDates.label(day("2027-02-28"), day("2027-03-03")))
    }

    @Test fun departureLabelAcrossNewYearShowsBothYears() {
        assertEquals("30 дек 2026 – 2 янв 2027", DepartureDates.label(day("2026-12-30"), day("2027-01-02")))
    }

    @Test fun departureLabelOneDayAndMissingEndUseGenitiveMay() {
        assertEquals("1 мая 2027", DepartureDates.label(day("2027-05-01"), day("2027-05-01")))
        assertEquals("1 мая 2027", DepartureDates.label(day("2027-05-01"), null))
        assertEquals("1–8 мая 2027", DepartureDates.label(day("2027-05-01"), day("2027-05-08")))
    }

    @Test fun departureLabelFallsBackToServerTextWithoutDates() {
        assertEquals("12–22 авг", DepartureDates.label(null, null, "12–22 авг"))
        assertEquals("12–22 авг 2026", DepartureDates.label(day("2026-08-12"), day("2026-08-22"), "старый текст"))
    }

    @Test fun departureTagGoesInFrontCapitalised() {
        assertEquals("Сакура · 28 мар – 7 апр 2027", DepartureDates.tagged("28 мар – 7 апр 2027", "сакура"))
        assertEquals("НГ · 30 дек 2026 – 2 янв 2027", DepartureDates.tagged("30 дек 2026 – 2 янв 2027", "НГ"))
        assertEquals("1–8 мая 2027", DepartureDates.tagged("1–8 мая 2027", "  "))
        assertEquals("1–8 мая 2027", DepartureDates.tagged("1–8 мая 2027", null))
        assertEquals("Сакура", DepartureDates.tagged("", "сакура"))
    }

    // MARK: - PhoneNumber (iOS PhoneNumberTests)

    @Test fun phoneE164FromVariousFormats() {
        assertEquals("+79990000000", PhoneNumber.e164("+7 (999) 000-00-00"))
        assertEquals("+79990000000", PhoneNumber.e164("89990000000"))
        assertEquals("+79990000000", PhoneNumber.e164("79990000000"))
        assertEquals("+79990000000", PhoneNumber.e164("9990000000"))
    }

    @Test fun phoneEmptyStaysEmpty() {
        assertEquals("", PhoneNumber.e164(""))
        assertEquals("", PhoneNumber.e164("   "))
        assertEquals("", PhoneNumber.digits("abc"))
    }

    @Test fun phoneE164IsIdempotent() {
        val once = PhoneNumber.e164("8 999 000 00 00")
        assertEquals(once, PhoneNumber.e164(once))
    }

    @Test fun phonePlusNumbersAreNotRussified() {
        assertEquals("819012345678", PhoneNumber.digits("+81 90 1234 5678"))
        assertEquals("+3725123456", PhoneNumber.e164("+372 5123 456"))
        assertEquals("79990000000", PhoneNumber.digits("+7 999 000 00 00"))
    }

    @Test fun phonePrettyUsesEachCountrysFormat() {
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("+79990000000"))
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("89990000000"))
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("9990000000"))
        assertEquals("+7 999 000-00-00", PhoneNumber.pretty("+7 999 000-00-00"))
        assertEquals("+31 6 86432839", PhoneNumber.pretty("+31686432839"))
        assertEquals("+7 701 123-45-67", PhoneNumber.pretty("+77011234567"))
        assertEquals("+1 201 555 0123", PhoneNumber.pretty("+12015550123"))
        assertEquals("", PhoneNumber.pretty(""))
    }

    // MARK: - ReturningCustomer (iOS ReturningCustomerTests, 0176 my_welcome)

    @Test fun returningCustomerNeedsANameAndATrip() {
        assertEquals(ReturningCustomer("Галина (тест 3)", 3), ReturningCustomer.from(" Галина (тест 3) ", 3))
        assertNull(ReturningCustomer.from("Галина", 0))
        assertNull(ReturningCustomer.from("Галина", null))
        assertNull(ReturningCustomer.from("  ", 2))
        assertNull(ReturningCustomer.from(null, 2))
    }

    // MARK: - UserRole + DirectMessagePolicy (iOS UserRoleTests, DirectMessagePolicyTests)

    @Test fun unknownAndEmptyRolesStayClients() {
        assertEquals(UserRole.Client, UserRole.fromRaw(null))
        assertEquals(UserRole.Client, UserRole.fromRaw(""))
        assertEquals(UserRole.Client, UserRole.fromRaw("coo"))
        assertEquals(UserRole.Client, UserRole.fromRaw("CLIENT"))
        assertEquals(UserRole.Guide, UserRole.fromRaw("guide"))
    }

    @Test fun staffGetTheFullTravellerApp() {
        // TNA-60: guides, curators, managers and Аня travel with us too.
        UserRole.entries.filter { it != UserRole.Partner }.forEach { assertTrue("$it", it.canBookAndEarn) }
        assertFalse(UserRole.Partner.canBookAndEarn)
    }

    @Test fun onlyClientsSeeTheReferralPromo() {
        assertTrue(UserRole.Client.canRefer)
        UserRole.entries.filter { it != UserRole.Client }.forEach { assertFalse("$it", it.canRefer) }
    }

    @Test fun managementIsManagersAndAdmin() {
        assertTrue(UserRole.Manager.isManagement)
        assertTrue(UserRole.Admin.isManagement)
        listOf(UserRole.Client, UserRole.Guide, UserRole.Curator, UserRole.Partner).forEach { assertFalse("$it", it.isManagement) }
    }

    @Test fun partnersCannotBeWrittenToOrWrite() {
        assertFalse(UserRole.Partner.canDirectMessage)
        for (role in UserRole.entries.filter { it != UserRole.Partner }) {
            assertTrue(role.canDirectMessage)
            assertFalse(role.mayStartDirectMessage(UserRole.Partner))
            assertFalse(UserRole.Partner.mayStartDirectMessage(role))
        }
        assertFalse(UserRole.Partner.mayStartDirectMessage(UserRole.Partner))
    }

    @Test fun nobodyWritesToAnyaFirstAndAClientOnlyReachesTheCrew() {
        UserRole.entries.forEach { assertFalse("$it → admin", it.mayStartDirectMessage(UserRole.Admin)) }
        assertTrue(UserRole.Client.mayStartDirectMessage(UserRole.Guide))
        assertTrue(UserRole.Client.mayStartDirectMessage(UserRole.Curator))
        assertTrue(UserRole.Client.mayStartDirectMessage(UserRole.Manager))
        assertFalse(UserRole.Client.mayStartDirectMessage(UserRole.Client))
        assertTrue(UserRole.Guide.mayStartDirectMessage(UserRole.Client))
        assertTrue(UserRole.Manager.mayStartDirectMessage(UserRole.Guide))
        assertTrue(UserRole.Admin.mayStartDirectMessage(UserRole.Client))
    }

    @Test fun unloadedPolicyMatchesBuiltInMatrixForEveryPair() {
        val policy = DirectMessagePolicy(null)
        for (me in UserRole.entries) for (target in UserRole.entries) {
            assertEquals("$me → $target", me.mayStartDirectMessage(target), policy.allows(me, target))
        }
    }

    @Test fun loadedPolicyHonoursServerListAndIgnoresTheCaller() {
        val policy = DirectMessagePolicy(listOf(UserRole.Guide, UserRole.Admin))
        assertTrue(policy.allows(UserRole.Client, UserRole.Guide))
        assertTrue(policy.allows(UserRole.Client, UserRole.Admin))
        assertFalse(policy.allows(UserRole.Client, UserRole.Manager))
        assertFalse(policy.allows(UserRole.Client, UserRole.Curator))
        assertTrue(policy.allows(UserRole.Admin, UserRole.Guide))
    }

    @Test fun emptyPolicyAllowsNothing() {
        val policy = DirectMessagePolicy(emptyList())
        for (me in UserRole.entries) for (target in UserRole.entries) {
            assertFalse("$me → $target", policy.allows(me, target))
        }
    }

    // MARK: - Referral + bonuses (iOS ReferralTests)

    @Test fun referralMapsServerStatusStrings() {
        assertEquals(ReferralResult.Ok, ReferralResult.fromRaw("ok"))
        assertEquals(ReferralResult.Already, ReferralResult.fromRaw("already"))
        assertEquals(ReferralResult.NotFound, ReferralResult.fromRaw("notfound"))
        assertEquals(ReferralResult.OwnCode, ReferralResult.fromRaw("self"))
        assertEquals(ReferralResult.Unauthorized, ReferralResult.fromRaw("unauthorized"))
        assertEquals(ReferralResult.Existing, ReferralResult.fromRaw("existing"))
        assertEquals(ReferralResult.Error, ReferralResult.fromRaw("garbage"))
        assertEquals(ReferralResult.Error, ReferralResult.fromRaw(null))
        ReferralResult.entries.forEach { assertTrue(it.userMessage.isNotBlank()) }
    }

    @Test fun bonusProfileLineNamesOnlyBucketsWithMoney() {
        val both = BonusSummary(available = 5_000, pending = 6_000, bound = true)
        assertTrue(both.profileLine!!.contains("доступно"))
        assertTrue(both.profileLine!!.contains("ожидает"))
        assertEquals("50006000", both.profileLine!!.filter { it.isDigit() })
        assertEquals("доступно 5 000 ₽", BonusSummary(5_000, 0, true).profileLine)
        assertNull(BonusSummary(0, 0, false).profileLine)
    }

    @Test fun bonusExpiryShownOnlyWhileMoneyCanStillBurn() {
        assertEquals("Сгорят 12.10.28", BonusSummary(1_000, 0, true, "2028-10-12").expiresLine)
        assertNull(BonusSummary(0, 500, true, "2028-10-12").expiresLine)
        assertNull(BonusSummary(1_000, 0, true, "").expiresLine)
        assertNull(BonusSummary(1_000, 0, true, null).expiresLine)
        assertEquals("12.10.28", BonusCopy.shortDate("2028-10-12"))
        assertEquals("garbage", BonusCopy.shortDate("garbage"))
    }

    private fun line(kind: String, amount: Int, remaining: Int, state: String, expires: String? = null, title: String = "") =
        BonusLine(UUID.randomUUID(), kind, amount, remaining, state, expires, title)

    @Test fun bonusLedgerLineNamesTheTripTheReasonAndWhatIsLeft() {
        val own = line("own_trip", 10_000, 4_000, "available", "2028-10-12", " Алтай ")
        assertEquals("Алтай · за свою поездку · 4 000 ₽ из 10 000 ₽ · доступно · до 12.10.28", own.text)

        val friend = line("referral_first", 6_000, 6_000, "pending", "2028-10-12")
        assertEquals("за первую поездку друга · 6 000 ₽ · ожидает", friend.text)

        assertTrue(line("legacy", 1, 1, "spent").text.contains("старая программа лояльности"))
        assertFalse(line("legacy", 1, 1, "spent").text.contains("за свою поездку"))
        assertTrue(line("manual", 1, 1, "expired").text.contains("от команды TNA"))
        assertTrue(line("referral_repeat", 1, 1, "reversed").text.endsWith("за поездку друга · 1 ₽ · снято"))
    }

    @Test fun bookingNoteMentionsCodeDiscountAndSpentBonuses() {
        assertEquals("Скидка по коду 6 000 ₽", BonusCopy.bookingNote(6_000, 0))
        assertEquals("Бонусами закрыто 3 000 ₽", BonusCopy.bookingNote(0, 3_000))
        assertEquals("Скидка по коду 6 000 ₽, бонусами закрыто 3 000 ₽", BonusCopy.bookingNote(6_000, 3_000))
        assertNull(BonusCopy.bookingNote(0, 0))
        val summary = BookingSummary(UUID.randomUUID(), "Алтай", "", BookingStatus.Paid, "", codeDiscount = 6_000)
        assertEquals("Скидка по коду 6 000 ₽", summary.moneyNote)
    }

    // MARK: - Display rules (iOS DisplayLogicTests)

    @Test fun bookingPipelineIsTheHappyPathWithoutCancelled() {
        assertEquals(
            listOf(BookingStatus.Requested, BookingStatus.Confirmed, BookingStatus.Prepaid, BookingStatus.Paid, BookingStatus.Completed),
            BookingStatus.pipeline,
        )
        assertFalse(BookingStatus.pipeline.contains(BookingStatus.Cancelled))
        BookingStatus.entries.forEach { assertTrue(it.label.isNotBlank()) }
        assertEquals(BookingStatus.Prepaid, BookingStatus.fromRaw("prepaid"))
        assertEquals(BookingStatus.Requested, BookingStatus.fromRaw("nonsense"))
    }

    @Test fun pluralCoversTeensAndBigNumbers() {
        val cases = mapOf(0 to "туров", 1 to "тур", 2 to "тура", 4 to "тура", 5 to "туров", 11 to "туров",
            12 to "туров", 14 to "туров", 21 to "тур", 22 to "тура", 25 to "туров", 101 to "тур", 111 to "туров", -1 to "тур")
        for ((n, expected) in cases) assertEquals("$n", expected, pluralRU(n, "тур", "тура", "туров"))
    }

    @Test fun statTitleAgreesWithItsValue() {
        fun item(n: Int) = StatItem("День в пути", "Дня в пути", "Дней в пути", n, "", "sun")
        assertEquals("День в пути", item(1).title)
        assertEquals("Дня в пути", item(2).title)
        assertEquals("Дней в пути", item(20).title)
        assertEquals("Высшая точка", StatItem.fixed("Высшая точка", 2_840, "м", "mountain").title)
        assertEquals("Высшая точка", StatItem.fixed("Высшая точка", 1, "м", "mountain").title)
    }

    @Test fun moneyGroupsThousandsWithSpaces() {
        assertEquals("0", Money.grouped(0))
        assertEquals("999", Money.grouped(999))
        assertEquals("1 000", Money.grouped(1_000))
        assertEquals("1 234 567", Money.grouped(1_234_567))
        assertEquals("-12 500", Money.grouped(-12_500))
        assertEquals("150 000 ₽", Money.rubles(150_000))
    }

    @Test fun travelerLevelProgressIsClampedBetweenRungs() {
        assertEquals(0.0, TravelerLevel.Novice.progress(0), 0.0001)
        assertEquals(0.5, TravelerLevel.Traveler.progress(2), 0.0001)
        assertEquals(1.0, TravelerLevel.Traveler.progress(10), 0.0001)
        assertEquals(0.0, TravelerLevel.Pioneer.progress(1), 0.0001)
        assertEquals(1.0, TravelerLevel.Legend.progress(100), 0.0001)
    }

    @Test fun passportCopy() {
        assertEquals("с TNA с 2023", PassportCopy.memberSince(2023))
        assertEquals("", PassportCopy.memberSince(1999))
        assertEquals("", PassportCopy.memberSince(null))
        assertEquals("Аня К.", PassportCopy.shortName(" Аня Козикова ", isMe = false))
        assertEquals("Аня Козикова", PassportCopy.shortName(" Аня Козикова ", isMe = true))
        assertEquals("Аня", PassportCopy.shortName("Аня", isMe = false))
        assertEquals(PassportCopy.stampAngle(0), PassportCopy.stampAngle(7), 0.0)
        assertNull(PassportCopy.monthYear("garbage"))
        assertEquals(listOf("Тур", "Страны", "Километров", "Дней в пути", "Высшая точка"),
            PassportCopy.stats(1, 2, 5, 11, 4_000).map { it.title })
        assertFalse(PassportData.empty.isLoaded)
    }

    // MARK: - Chats

    @Test fun unreadFlagPrefersTheOthersLatestMessage() {
        val read = Instant.parse("2026-01-01T10:00:00Z")
        val before = read.minusSeconds(60)
        val after = read.plusSeconds(60)
        assertEquals(1, ChatUnread.flag(after, after, newestIsMine = true, lastRead = read))
        assertEquals(0, ChatUnread.flag(before, after, newestIsMine = false, lastRead = read))
        assertEquals(1, ChatUnread.flag(null, after, newestIsMine = false, lastRead = read))
        assertEquals(0, ChatUnread.flag(null, after, newestIsMine = true, lastRead = read))
        assertEquals(0, ChatUnread.flag(null, before, newestIsMine = false, lastRead = read))
        assertEquals(0, ChatUnread.flag(null, null, newestIsMine = false, lastRead = read))
    }

    @Test fun supportPlaceholderIsPinnedSupport() {
        val s = ChatThread.supportPlaceholder
        assertTrue(s.isSupport)
        assertTrue(s.isPinned)
        assertEquals(ChatThreadKind.Support, s.kind)
        assertTrue(s.belongsInChatList)
    }

    // MARK: - News, countries, certificates

    @Test fun newsLinkRejectsNonWebUrlsAndBrokenTourIds() {
        assertNull(newsLink("url", "javascript:alert(1)"))
        assertNull(newsLink("url", null))
        assertNull(newsLink("tour", null))
        assertNull(newsLink(null, "x"))
        assertEquals(NewsLink.UrlLink("http://a.ru"), newsLink("url", "http://a.ru"))
        assertFalse(newsWindowOver(null, Instant.now()))
        assertFalse(newsWindowOver("garbage", Instant.now()))
    }

    @Test fun countryStatusFromRawAndBeen() {
        assertEquals(CountryStatus.Lives, CountryStatus.fromRaw("lives"))
        assertEquals(CountryStatus.Wants, CountryStatus.fromRaw("WANTS"))
        assertNull(CountryStatus.fromRaw("nope"))
        assertTrue(CountryStatus.Lives.isBeen)
        assertTrue(CountryStatus.Visited.isBeen)
        assertFalse(CountryStatus.Wants.isBeen)
    }

    @Test fun certificateCheckMessages() {
        assertNull(CertificateCheck.Applicable.userMessage)
        CertificateCheck.entries.filter { it != CertificateCheck.Applicable }
            .forEach { assertTrue("$it", !it.userMessage.isNullOrBlank()) }
    }

    @Test fun giftCertificateStatusAndShareText() {
        fun cert(status: String, expires: String? = null, recipient: String = "Маша", wish: String = "С днём рождения!") =
            GiftCertificate(UUID.randomUUID(), "TNA-AAAA-2222", 50_000, recipient, wish, status, pin = "0421", expiresOn = expires)
        assertEquals("Активен", cert("paid").statusTitle)
        assertEquals("Использован", cert("redeemed").statusTitle)
        assertEquals("Срок истёк", cert("expired").statusTitle)
        assertEquals("Отменён", cert("cancelled").statusTitle)
        assertEquals("Ждёт оплаты", cert("").statusTitle)
        assertEquals("Ждёт оплаты", cert("garbage").statusTitle)

        val text = cert("paid", "2027-03-08").shareText
        assertTrue(text.contains("TNA-AAAA-2222"))
        assertTrue(text.contains("0421"))
        assertTrue(text.contains("50 000 ₽"))
        assertTrue(text.contains("Для: Маша"))
        assertTrue(text.contains("«С днём рождения!»"))
        assertTrue(text.contains("Действует до 8 марта 2027"))

        val bare = cert("paid", recipient = " ", wish = "").shareText
        assertFalse(bare.contains("Для:"))
        assertFalse(bare.contains("«"))
        assertFalse(bare.contains("Действует"))

        // The picture carries no PIN, so the message next to it must.
        val message = cert("paid").shareMessage
        assertTrue(message.contains("TNA-AAAA-2222"))
        assertTrue(message.contains("0421"))
        assertTrue(message.contains("travelneveralone.com"))
    }

    @Test fun dataIssuesOpenTheSupportChatWithAGreeting() {
        DataIssue.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.prefill.startsWith("Здравствуйте!"))
        }
    }

    @Test fun datesParseDayToleratesTimestampsAndGarbage() {
        assertEquals(day("2026-08-12"), Dates.parseDay("2026-08-12T10:00:00Z"))
        assertNull(Dates.parseDay("12.08.2026"))
        assertNull(Dates.parseDay(null))
    }

    @Test fun departureSoldOutAndUpcoming() {
        assertTrue(Departure(label = "x", spotsLeft = 0).isSoldOut)
        assertFalse(Departure(label = "x", spotsLeft = 1).isSoldOut)
        assertTrue(Departure(label = "x", spotsLeft = 1).isUpcoming)
        assertFalse(Departure(label = "x", spotsLeft = 1, startsOn = Dates.todayUtc().minusDays(1)).isUpcoming)
        assertTrue(Departure(label = "x", spotsLeft = 1, startsOn = Dates.todayUtc()).isUpcoming)
    }

    @Test fun tripCountdownAndRunning() {
        fun trip(start: LocalDate?, end: LocalDate?, role: String = "client") = Trip(
            UUID.randomUUID(), UUID.randomUUID(), "Алтай", emptyList(), "", "", start, end, "planned", role, null,
        )
        val today = Dates.todayUtc()
        assertEquals("через 3 дня", trip(today.plusDays(3), today.plusDays(9)).countdown)
        assertEquals("старт сегодня", trip(today, today.plusDays(9)).countdown)
        assertEquals("в пути", trip(today.minusDays(1), today.plusDays(9)).countdown)
        assertEquals("", trip(null, null).countdown)
        assertTrue(trip(today.minusDays(1), today.plusDays(1)).isRunning)
        assertFalse(trip(today.plusDays(1), today.plusDays(5)).isRunning)
        assertFalse(trip(today.minusDays(5), today.minusDays(1)).isUpcoming)
        assertTrue(trip(null, null, "coordinator").isGuide)
        assertFalse(trip(null, null, "client").isGuide)
    }

    @Test fun tourPricesAndSale() {
        val tour = Mock.tours.first().copy(salePriceFrom = null, saleLabel = null)
        assertFalse(tour.hasSale)
        assertEquals(tour.priceFrom, tour.displayPrice)
        val sale = tour.copy(salePriceFrom = tour.priceFrom - 1_000)
        assertTrue(sale.hasSale)
        assertEquals("от ${Money.grouped(tour.priceFrom - 1_000)} ₽", sale.priceText)
        assertEquals("от ${Money.grouped(tour.priceFrom)} ₽", sale.basePriceText)
        assertFalse(tour.copy(salePriceFrom = tour.priceFrom).hasSale)
    }

    @Test fun tourHeroImagesFallBackToTheCover() {
        val tour = Mock.tours.first().copy(imageUrl = "cover", images = emptyList())
        assertEquals(listOf("cover"), tour.heroImages)
        assertEquals(listOf("a", "b"), tour.copy(images = listOf("a", "b")).heroImages)
        assertEquals(emptyList<String>(), tour.copy(imageUrl = null).heroImages)
    }

    @Test fun bookingRequestSubjectSkipsEmptyParts() {
        assertEquals("Алтай · 1–8 мая 2027", BookingRequest("Алтай", "1–8 мая 2027", "Слава", "+7").subject)
        assertEquals("Алтай", BookingRequest("Алтай", "", "Слава", "+7").subject)
        assertEquals("Хличкин Вячеслав", TravelCompanion(lastName = "Хличкин", firstName = "Вячеслав").fullName)
    }
}
