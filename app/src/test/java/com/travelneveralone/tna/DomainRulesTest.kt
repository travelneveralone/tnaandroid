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

    @Test fun newsShowWindowEnd() {
        // 0141: show_until is exclusive; no end (or no column) never hides.
        val end = "2027-01-09T00:00:00Z"
        assertFalse(newsWindowOver(end, Instant.parse("2027-01-08T23:59:00Z")))
        assertTrue(newsWindowOver(end, Instant.parse("2027-01-09T00:00:00Z")))
        assertFalse(newsWindowOver(null, Instant.parse("2099-01-01T00:00:00Z")))
    }

    @Test fun newsLinkFromColumns() {
        val id = UUID.randomUUID()
        assertEquals(NewsLink.TourLink(id), newsLink("tour", id.toString()))
        assertEquals(NewsLink.UrlLink("https://t.me/x"), newsLink("url", "https://t.me/x"))
        assertNull(newsLink("tour", "not-a-uuid"))
        assertNull(newsLink(null, null))
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
        assertTrue(UserRole.Guide.canBookAndEarn)
        assertTrue(UserRole.Manager.canBookAndEarn)
        assertFalse(UserRole.Partner.canBookAndEarn)
        assertTrue(UserRole.Client.canRefer)
        assertFalse(UserRole.Manager.canRefer)
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

    @Test fun emptyDirectShellHiddenFromChatList() {
        val empty = ChatThread(
            title = "Мария", subtitle = "личные сообщения", lastMessage = "", time = "",
            unread = 0, icon = "person", colors = emptyList(), isPinned = false,
            kind = ChatThreadKind.Direct, lastActivity = null,
        )
        val withMsg = empty.copy(lastActivity = Instant.parse("2026-11-12T21:56:07Z"), lastMessage = "привет")
        val trip = empty.copy(kind = ChatThreadKind.Trip, title = "Лансароте", lastActivity = null)
        assertFalse(empty.belongsInChatList)
        assertTrue(withMsg.belongsInChatList)
        assertTrue(trip.belongsInChatList)
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
        assertFalse(wait.showsReferralField)
    }

    @Test fun customDatesAndCertificate() {
        val tour = Mock.tours.first()
        val custom = BookingForm(tour, null, "Анна", "+79990000006")
        assertFalse(custom.canSubmit)
        custom.startDate = LocalDate.of(2027, 3, 3)
        custom.endDate = LocalDate.of(2027, 3, 9)
        assertTrue(custom.canSubmit)
        assertEquals("Свои даты: 3–9 мар", custom.departureText)
        assertEquals("28 фев – 5 мар", BookingForm.rangeLabel(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 5)))
        assertEquals("Даты (свои): 3–9 мар", custom.details()[1])
        assertEquals("TNA-AB12-CD34", CertificateRedemption.formattedCode("tnaab12cd34"))
        assertEquals("TNA-", CertificateRedemption.formattedCode("TN"))
        assertEquals("1234", CertificateRedemption.formattedPin("12a345"))
        val cert = CertificateRedemption()
        cert.updateCode("AB12CD34")
        cert.updatePin("1234")
        assertTrue(cert.canCheck)
    }

    @Test fun ownCertificatePicks() {
        fun c(code: String, pin: String, status: String, expires: String?) =
            GiftCertificate(UUID.randomUUID(), code, 10_000, "", "", status, pin = pin, expiresOn = expires)
        val all = listOf(
            c("TNA-AAAA-0001", "1234", "paid", "2027-11-20"),
            c("TNA-AAAA-0002", "5678", "paid", null),
            c("TNA-AAAA-0003", "1111", "redeemed", "2027-11-20"),
            c("TNA-AAAA-0004", "2222", "pending", "2027-11-20"),
            c("TNA-AAAA-0005", "3333", "paid", "2026-11-19"),
        )
        val usable = CertificateRedemption.usable(all, LocalDate.of(2026, 11, 20))
        assertEquals(listOf("TNA-AAAA-0001", "TNA-AAAA-0002"), usable.map { it.code })
        val cert = CertificateRedemption()
        cert.pick(usable[1])
        assertEquals("TNA-AAAA-0002", cert.code)
        assertEquals("5678", cert.pin)
        assertTrue(cert.canCheck)
        assertTrue(cert.isPicked(usable[1]))
        assertFalse(cert.isPicked(usable[0]))
        cert.updatePin("5679")
        assertFalse(cert.isPicked(usable[1]))
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

    @Test fun salePlateOnlyForRealSale() {
        val tour = Tour(
            name = "Байкал", tagline = "", hashtag = "", region = TourRegion.Russia,
            days = 5, km = 0, priceFrom = 90000, difficulty = 1, maxAltitude = 0,
            symbol = "snowflake", colors = emptyList(),
            departures = listOf(Departure(label = "", spotsLeft = 5, startsOn = LocalDate.of(2027, 2, 1))),
            program = emptyList(), includes = emptyList(),
        )
        assertNull(SalePlate.make(tour, null))
        val sale = tour.copy().apply { salePriceFrom = 80000; saleLabel = "Чёрная пятница" }
        val plate = SalePlate.make(sale, "2026-11-30")!!
        assertEquals("БАЙКАЛ", plate.name)
        assertEquals("5 ДНЕЙ", plate.daysLine)
        assertEquals("ЧЁРНАЯ ПЯТНИЦА", plate.mark)
        assertEquals("ДО 30 НОЯБРЯ", plate.until)
        assertEquals("ДО 30.11", plate.untilShort)
        assertEquals("", SalePlate.untilLine(null))
        assertEquals("ДО 01.01", SalePlate.untilShortLine("2026-01-01"))
        assertEquals("", SalePlate.untilShortLine(null))
    }

    @Test
    fun pollDraftQuizDropsMultiAndRevote() {
        val a = PollDraftOption(text = " Да ")
        val b = PollDraftOption(text = "Нет")
        val empty = PollDraftOption(text = "  ")
        val poll = PollDraft(question = " Едем? ", options = listOf(a, empty, b), multiple = true, quiz = true, correct = setOf(b.id)).build()
        assertEquals("Едем?", poll.question)
        assertEquals(listOf("Да", "Нет"), poll.options.map { it.text })
        assertEquals(false, poll.multiple)
        assertEquals(false, poll.revote)
    }

    @Test
    fun staffTagOnlyForTeam() {
        assertEquals(null, staffTag(false, "client"))
        assertEquals("менеджер", staffTag(false, "manager"))
    }

    @Test
    fun togetherTextAsGuideAndTraveller() {
        assertEquals(null, PublicProfile(togetherCount = 0).togetherText)
        assertEquals("Вёл твою поездку", PublicProfile(togetherCount = 1, togetherAsGuide = true).togetherText)
        assertEquals("Ездили вместе в 2 поездках", PublicProfile(togetherCount = 2).togetherText)
    }

    @Test
    fun tripPackIconsAndStatus() {
        assertEquals("airplane", TripPackMapper.icon("Вылет в Петропавловск"))
        assertEquals("fork.knife", TripPackMapper.icon("Ужин у костра"))
        assertEquals("mappin.and.ellipse", TripPackMapper.icon("Что-то ещё"))
        assertEquals("День 2 из 7 · Вулкан", TripPackMapper.statusLine("active", 2, 7, "Вулкан", null))
        val start = java.time.LocalDate.of(2026, 12, 10)
        assertTrue(TripPackMapper.statusLine("upcoming", null, 7, null, start, today = start).startsWith("Старт сегодня · 10 "))
        assertTrue(TripPackMapper.statusLine("upcoming", null, 7, null, start, today = start.minusDays(3)).endsWith("через 3 дня"))
    }

    /** Same cases as iOS DepartureDatesTests and SQL departure_label() (0168). */
    @Test
    fun departureLabelAlwaysHasTheYear() {
        val d = { s: String -> LocalDate.parse(s) }
        assertEquals("21–23 фев 2027", DepartureDates.label(d("2027-02-21"), d("2027-02-23")))
        assertEquals("28 фев – 3 мар 2027", DepartureDates.label(d("2027-02-28"), d("2027-03-03")))
        assertEquals("30 дек 2026 – 2 янв 2027", DepartureDates.label(d("2026-12-30"), d("2027-01-02")))
        assertEquals("1 мая 2027", DepartureDates.label(d("2027-05-01"), d("2027-05-01")))
        assertEquals("1 мая 2027", DepartureDates.label(d("2027-05-01"), null))
        assertEquals("1–8 мая 2027", DepartureDates.label(d("2027-05-01"), d("2027-05-08")))
        assertEquals("12–22 авг", DepartureDates.label(null, null, "12–22 авг"))
        assertEquals("Сакура · 28 мар – 7 апр 2027", DepartureDates.tagged("28 мар – 7 апр 2027", "сакура"))
        assertEquals("НГ · 1 мая 2027", DepartureDates.tagged("1 мая 2027", "НГ"))
        assertEquals("1 мая 2027", DepartureDates.tagged("1 мая 2027", " "))
    }

    @Test
    fun nameDisplayVariants() {
        assertEquals(listOf("Аня", "Аня Сергеевна", "Козикова Аня", "Козикова А.", "Аня К."), NameDisplay.variants("Козикова", "Аня", "Сергеевна"))
        assertEquals(listOf("Аня"), NameDisplay.variants("", "Аня", ""))
    }

    @Test
    fun avatarCropKeepsCircleCovered() {
        // 200x100 photo in a 100 circle: fill scale 1, 100 px of horizontal slack.
        assertEquals(1f, AvatarCrop.fillScale(100f, 200f, 100f, 1f), 0.001f)
        assertEquals(50f to 0f, AvatarCrop.clamp(80f, 30f, 100f, 200f, 100f, 1f))
        assertEquals(Triple(50f, 0f, 100f), AvatarCrop.cropRect(100f, 200f, 100f, 1f, 0f, 0f))
        // Zoom x2 shrinks the cut square to half.
        assertEquals(50f, AvatarCrop.cropRect(100f, 200f, 100f, 2f, 0f, 0f).third, 0.001f)
    }

    @Test
    fun featuredVideoPolicy() {
        assertTrue(FeaturedVideoPolicy(screenOnTop = true, systemAllowsAutoplay = true, lowPowerMode = false).mayPlay)
        // Behind another tab the clip stays mounted but paused.
        val behind = FeaturedVideoPolicy(screenOnTop = false, systemAllowsAutoplay = true, lowPowerMode = false)
        assertTrue(behind.mountsClip)
        assertFalse(behind.mayPlay)
        // Power saving or no animations: the card keeps its photo.
        assertFalse(FeaturedVideoPolicy(true, true, lowPowerMode = true).mountsClip)
        assertFalse(FeaturedVideoPolicy(true, systemAllowsAutoplay = false, lowPowerMode = false).mountsClip)
    }

    @Test
    fun videoGeometry() {
        assertEquals(0.5f, VideoGeometry.visibleFraction(100f, 100f, 50f, 100f), 0.001f)
        assertEquals(0f, VideoGeometry.visibleFraction(0f, 100f, 0f, 100f), 0.001f)
        // 16:9 clip in a portrait card: stretched width grows, height stays.
        val (sx, sy) = VideoGeometry.fillScale(290f, 400f, 1920f, 1080f)
        assertEquals(1f, sy, 0.001f)
        assertEquals(1920f * (400f / 1080f) / 290f, sx, 0.001f)
    }

    @Test
    fun videoCacheFileNameIsStable() {
        val a = TourVideoCache.fileName("https://cdn.example.com/clips/kamchatka.MOV?token=1")
        assertEquals(a, TourVideoCache.fileName("https://cdn.example.com/clips/kamchatka.MOV?token=1"))
        assertTrue(a.endsWith(".mov"))
        assertTrue(TourVideoCache.fileName("https://cdn.example.com/stream").endsWith(".mp4"))
    }

    @Test
    fun underFourteenSuggestsBirthCertificate() {
        val at = LocalDate.of(2026, 11, 14)
        assertEquals(14, DocKind.RF_PASSPORT_MIN_AGE)
        assertEquals(13, DocKind.ageYears("2013-11-14", at))
        assertEquals(14, DocKind.ageYears("2012-11-14", at))
        assertEquals(DocKind.BirthCert, DocKind.suggestedKind("2013-11-14", null, "", at))
        assertNull(DocKind.suggestedKind("2012-11-14", null, "", at))
        assertEquals(DocKind.PassportIntl, DocKind.suggestedKind("2013-11-14", DocKind.PassportIntl, "", at))
        assertNull(DocKind.suggestedKind("2013-11-14", null, "IV-МЮ 1", at))
        assertEquals(
            DocKind.BirthCert,
            IdentityDoc().withSuggestedKind("2013-11-14", at).kind,
        )
    }

    @Test
    fun countryMapCycleAndCounters() {
        // Map tap order matches iOS: был → живу → хочу → снять.
        assertEquals(CountryStatus.Visited, CountryMapRules.next(null))
        assertEquals(CountryStatus.Lives, CountryMapRules.next(CountryStatus.Visited))
        assertEquals(CountryStatus.Wants, CountryMapRules.next(CountryStatus.Lives))
        assertNull(CountryMapRules.next(CountryStatus.Wants))
        val mine = mapOf("RUS" to CountryStatus.Lives, "ITA" to CountryStatus.Wants)
        val tna = setOf("RUS", "GEO")
        // A TNA country counts as «был» unless the person set something else.
        assertEquals(CountryStatus.Visited, CountryMapRules.display(mine, tna)["GEO"])
        assertEquals(CountryStatus.Lives, CountryMapRules.display(mine, tna)["RUS"])
        assertEquals(2, CountryMapRules.beenCount(mine, tna))
        assertEquals(1, CountryMapRules.wantsCount(mine))
    }

    @Test
    fun countryListFilters() {
        val all = listOf("GEO" to "Грузия", "ITA" to "Италия", "RUS" to "Россия")
        val display = mapOf("ITA" to CountryStatus.Wants, "RUS" to CountryStatus.Lives)
        val every = CountryStatus.entries.toSet()
        assertEquals(all, CountryMapRules.filtered(all, display, every, ""))
        assertEquals(listOf("ITA" to "Италия"), CountryMapRules.filtered(all, display, setOf(CountryStatus.Wants), ""))
        // Search ignores the legend filter.
        assertEquals(listOf("GEO" to "Грузия"), CountryMapRules.filtered(all, display, setOf(CountryStatus.Wants), "груз"))
    }

    @Test
    fun flyoverRegionIsCentredOnPlace() {
        val place = VisitedPlace(name = "Камчатка", latitude = 53.0, longitude = 158.0, color = 0)
        val r = MapRegion.around(place)
        assertEquals(158.0, r.minLon + r.lonSpan / 2, 0.001)
        assertEquals(53.0, r.minLat + r.latSpan / 2, 0.001)
    }
}
