package com.travelneveralone.tna

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Profile, passport and trip-pack rules (iOS ProfileExtrasTests, PublicProfileTests,
 * TripPackMapperTests, EmailVerify) plus the «Кто едет» block the manager reads
 * (iOS CompanionDocFormatTests) and its round trip through ParsedBookingNote
 * (iOS BookingNoteParseTests).
 */
class ProfileRulesTest {
    // MARK: - Identity documents

    @Test fun docKindMapsKnownTypesOnly() {
        assertNull(IdentityDoc(type = "").kind)
        assertNull(IdentityDoc(type = "driver").kind)
        assertEquals(DocKind.BirthCert, IdentityDoc(type = "birth_cert").kind)
        assertTrue(IdentityDoc().isEmpty)
        assertFalse(IdentityDoc(number = "1").isEmpty)
        assertTrue(DocKind.PassportIntl.hasExpiry)
        assertFalse(DocKind.PassportRF.hasExpiry)
        assertTrue(DocKind.PassportRF.hasInternalFields)
        assertFalse(DocKind.BirthCert.hasInternalFields)
    }

    @Test fun ageCountsWholeYearsOnTheBirthday() {
        val at = LocalDate.of(2026, 11, 21)
        assertEquals(13, DocKind.ageYears("2012-11-22", at))
        assertEquals(14, DocKind.ageYears("2012-11-21", at))
        assertEquals(14, DocKind.ageYears("2012-11-21T00:00:00Z", at))
        assertNull(DocKind.ageYears("garbage", at))
        assertNull(DocKind.ageYears(null, at))
    }

    @Test fun suggestedDocumentRespectsWhatIsAlreadyFilled() {
        val at = LocalDate.of(2026, 11, 21)
        val doc = IdentityDoc().withSuggestedKind("2015-01-01", at)
        assertEquals("birth_cert", doc.type)
        assertEquals(IdentityDoc(type = "passport_intl"), IdentityDoc(type = "passport_intl").withSuggestedKind("2015-01-01", at))
        assertEquals(IdentityDoc(number = "IV-МЮ 1"), IdentityDoc(number = "IV-МЮ 1").withSuggestedKind("2015-01-01", at))
        assertEquals(IdentityDoc(), IdentityDoc().withSuggestedKind("1990-01-01", at))
    }

    @Test fun citizenshipPinsPostSovietCountriesFirst() {
        val all = Citizenship.all
        assertEquals("Россия", Citizenship.defaultValue)
        assertEquals("Россия", all.first())
        assertEquals(Citizenship.priority, all.take(Citizenship.priority.size))
        assertEquals(all.size, all.toSet().size)
        val others = Citizenship.others
        assertEquals(others.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("ru"))) { it }), others)
    }

    // MARK: - Names

    @Test fun nameVariantsFromParts() {
        val v = NameDisplay.variants(" Иванов ", "Пётр", "Сергеевич")
        assertEquals(listOf("Пётр", "Пётр Сергеевич", "Иванов Пётр", "Иванов П.", "Пётр И."), v)
        assertEquals(listOf("Аня"), NameDisplay.variants("", "Аня", ""))
        assertTrue(NameDisplay.variants("", "", "").isEmpty())
        assertTrue(NameDisplay.variants("Иванов", "", "Сергеевич").isEmpty())
    }

    @Test fun emailVerifyResultFromServer() {
        assertEquals(EmailVerifyResult.Ok, EmailVerifyResult.from(true, "wrong_code"))
        assertEquals(EmailVerifyResult.WrongCode, EmailVerifyResult.from(false, "wrong_code"))
        assertEquals(EmailVerifyResult.Expired, EmailVerifyResult.from(null, "expired"))
        assertEquals(EmailVerifyResult.TooManyAttempts, EmailVerifyResult.from(false, "too_many_attempts"))
        assertEquals(EmailVerifyResult.InvalidEmail, EmailVerifyResult.from(false, "invalid_email"))
        assertEquals(EmailVerifyResult.SendFailed, EmailVerifyResult.from(false, "send_failed"))
        assertEquals(EmailVerifyResult.Expired, EmailVerifyResult.from(false, "no_code"))
        assertEquals(EmailVerifyResult.Cooldown, EmailVerifyResult.from(false, "cooldown"))
        assertEquals(EmailVerifyResult.Error, EmailVerifyResult.from(false, null))
        EmailVerifyResult.entries.forEach { assertTrue(it.userMessage.isNotBlank()) }
    }

    @Test fun emailCooldownCarriesTheWait() {
        assertEquals(EmailVerifyReply(EmailVerifyResult.Cooldown, 42), EmailVerifyReply.from(false, "cooldown", 42))
        // No retry_after from the server: the usual 60 s pause.
        assertEquals(60L, EmailVerifyReply.from(false, "cooldown", null).retryAfterSec)
        assertEquals(1L, EmailVerifyReply.from(false, "cooldown", 0).retryAfterSec)
        // Anything else has no wait.
        assertEquals(0L, EmailVerifyReply.from(true, null, 42).retryAfterSec)
        assertEquals(0L, EmailVerifyReply.from(false, "wrong_code", 42).retryAfterSec)
    }

    // MARK: - Someone else's passport (iOS PublicProfileTests)

    @Test fun publicProfileStatTilesSkipZeroes() {
        val p = PublicProfile(tours = 2, km = 6_600)
        assertEquals(listOf("Тура", "Километров"), p.statItems.map { it.title })
        assertTrue(p.hasTravelStats)
        assertFalse(PublicProfile().hasTravelStats)
    }

    @Test fun publicProfileTexts() {
        assertEquals("Ездили вместе в 1 поездке", PublicProfile(togetherCount = 1).togetherText)
        assertEquals("Ездили вместе в 3 поездках", PublicProfile(togetherCount = 3).togetherText)
        assertNull(PublicProfile(togetherCount = 0).togetherText)
        assertEquals("Вёл твою поездку", PublicProfile(togetherCount = 1, togetherAsGuide = true).togetherText)
        assertEquals("Вёл 5 твоих поездок", PublicProfile(togetherCount = 5, togetherAsGuide = true).togetherText)
        assertNull(PublicProfile(memberSinceYear = 1970).memberSinceText)
        assertEquals("с TNA с 2024", PublicProfile(memberSinceYear = 2024).memberSinceText)
        assertEquals("в команде TNA с 2021", PublicProfile(memberSinceYear = 2021, role = "guide").memberSinceText)
        assertEquals(TravelerLevel.Novice, PublicProfile(tours = 0).level)
        assertEquals(TravelerLevel.Pioneer, PublicProfile(tours = 4).level)
        assertEquals(TravelerLevel.Legend, PublicProfile(tours = 9).level)
    }

    @Test fun togetherTextNeverSlipsIntoPoliteForm() {
        val texts = (1..3).flatMap { n -> listOf(false, true).mapNotNull { PublicProfile(togetherCount = n, togetherAsGuide = it).togetherText } }
        assertEquals(6, texts.size)
        texts.forEach { assertFalse(it, it.lowercase().contains("ваш")) }
    }

    @Test fun newAchievementsCaptionAgreesWithTheNumber() {
        assertEquals("новое достижение", newAchievementsCaption(1))
        assertEquals("новых достижения", newAchievementsCaption(3))
        assertEquals("новых достижений", newAchievementsCaption(5))
        assertEquals("новое достижение", newAchievementsCaption(21))
    }

    // MARK: - Trip pack (iOS TripPackMapperTests)

    @Test fun tripStatusLinePerPhase() {
        val today = LocalDate.of(2026, 8, 9)
        assertEquals("День 3 из 11 · Мутновский вулкан", TripPackMapper.statusLine("active", 3, 11, "Мутновский вулкан", null, today))
        assertEquals("День 3 из 11", TripPackMapper.statusLine("active", 3, 11, null, null, today))
        assertEquals("Тур идёт", TripPackMapper.statusLine("active", null, 11, null, null, today))
        assertEquals("Тур завершён", TripPackMapper.statusLine("ended", null, 11, null, null, today))
        assertEquals("Скоро в путь", TripPackMapper.statusLine("upcoming", null, 11, null, null, today))
        assertEquals("Старт сегодня · 9 авг.", TripPackMapper.statusLine("upcoming", null, 11, null, today, today))
        assertEquals("Старт 12 авг. · через 3 дня", TripPackMapper.statusLine("upcoming", null, 11, null, LocalDate.of(2026, 8, 12), today))
        assertTrue(TripPackMapper.statusLine("upcoming", null, 11, null, today.minusDays(2), today).startsWith("Старт сегодня"))
    }

    @Test fun scheduleIconsFollowTheTitle() {
        assertEquals("airplane", TripPackMapper.icon("Прилёт в Петропавловск"))
        assertEquals("figure.hiking", TripPackMapper.icon("Треккинг к фумаролам"))
        assertEquals("mountain.2.fill", TripPackMapper.icon("Мутновский вулкан"))
        assertEquals("water.waves", TripPackMapper.icon("Бухта Русская"))
        assertEquals("flame.fill", TripPackMapper.icon("Термальные источники"))
        assertEquals("fork.knife", TripPackMapper.icon("Ужин"))
        assertEquals("car.fill", TripPackMapper.icon("Трансфер"))
        assertEquals("sparkles", TripPackMapper.icon("Свободный день"))
        assertEquals("mappin.and.ellipse", TripPackMapper.icon("Музей"))
    }

    // MARK: - Avatar crop

    @Test fun avatarCropCoversTheCircle() {
        assertEquals(1.0, AvatarCrop.fillScale(100f, 0f, 50f, 2f).toDouble(), 1e-6)
        assertEquals(0.5, AvatarCrop.fillScale(100f, 400f, 200f, 1f).toDouble(), 1e-6)
        val (x, y) = AvatarCrop.clamp(500f, -500f, 100f, 400f, 200f, 1f)
        assertEquals(50.0, x.toDouble(), 1e-6)
        assertEquals(0.0, y.toDouble(), 1e-6)
        val (left, top, side) = AvatarCrop.cropRect(100f, 400f, 200f, 1f, 0f, 0f)
        assertEquals(100.0, left.toDouble(), 1e-6)
        assertEquals(0.0, top.toDouble(), 1e-6)
        assertEquals(200.0, side.toDouble(), 1e-6)
    }

    // MARK: - «Кто едет» for the manager (iOS CompanionDocFormatTests)

    private fun companion(kind: DocKind?, number: String = "1234 567890") = TravelCompanion(
        lastName = "Иванов", firstName = "Иван", patronymic = "Иванович", birthday = "1990-05-01", phone = "89990000000",
        doc = IdentityDoc(type = kind?.raw ?: "", number = number, citizenship = "Россия", issued = "2015-06-01",
            expiry = "2025-06-01", issuedBy = "ОВД", registration = "Москва"),
    )

    @Test fun foreignPassportShowsIssuedAndExpiryNotInternalFields() {
        val text = CompanionFormat.block(1, companion(DocKind.PassportIntl))
        assertTrue(text.contains("Загран: 1234 567890"))
        assertTrue(text.contains("Выдан: 01.06.2015"))
        assertTrue(text.contains("До: 01.06.2025"))
        assertFalse(text.contains("Кем:"))
        assertFalse(text.contains("Прописка:"))
    }

    @Test fun internalPassportShowsIssuerAndRegistrationNoExpiry() {
        val text = CompanionFormat.block(1, companion(DocKind.PassportRF))
        assertTrue(text.contains("Паспорт РФ:"))
        assertTrue(text.contains("Кем: ОВД"))
        assertTrue(text.contains("Прописка: Москва"))
        assertFalse(text.contains("До:"))
    }

    @Test fun birthCertificateHasNoExpiryOrInternalFields() {
        val text = CompanionFormat.block(1, companion(DocKind.BirthCert, number = ""))
        assertTrue(text.contains("Свид. о рожд.: Свидетельство о рождении"))
        assertFalse(text.contains("До:"))
        assertFalse(text.contains("Кем:"))
    }

    @Test fun blockStartsWithNumberedNameAndNumericDates() {
        val text = CompanionFormat.block(2, companion(null))
        assertTrue(text.startsWith("2. Иванов Иван Иванович"))
        assertTrue(text.contains("Гражд: Россия"))
        assertTrue(text.contains("ДР: 01.05.1990"))
        assertTrue(text.contains("Тел: +7 999 000-00-00"))
        assertFalse(text.contains("Выдан:"))
        assertEquals("1. Без имени", CompanionFormat.block(1, TravelCompanion(lastName = " ", firstName = "")))
    }

    @Test fun pickerLineSummarisesOnePerson() {
        assertEquals("Иванов Иван Иванович · р. 01.05.1990 · +7 999 000-00-00 · Загран 1234 567890",
            CompanionFormat.line(companion(DocKind.PassportIntl)))
        assertEquals("Без имени", CompanionFormat.line(TravelCompanion(lastName = "", firstName = "")))
    }

    @Test fun messageRoundTripsThroughTheBookingNoteParser() {
        val people = listOf(companion(DocKind.PassportIntl), TravelCompanion(lastName = "Петрова", firstName = "Анна"))
        val text = CompanionFormat.message(people)
        assertTrue(text.startsWith("👥 Кто едет (2)"))
        assertTrue(text.contains("--------"))
        assertEquals("", CompanionFormat.message(emptyList()))

        val parsed = ParsedBookingNote("Заявка на тур: Лансароте · Свои даты: 29 авг – 5 сен\n$text")
        assertEquals("Заявка на тур: Лансароте · Свои даты: 29 авг – 5 сен", parsed.summary)
        assertEquals(listOf("Иванов Иван Иванович", "Петрова Анна"), parsed.companions.map { it.first })
        assertTrue(parsed.companions[0].second.contains("Тел: +7 999 000-00-00"))
        assertTrue(parsed.companions[1].second.isEmpty())
    }

    // MARK: - ParsedBookingNote (iOS BookingNoteParseTests)

    @Test fun noteWithoutCompanionsIsAllSummary() {
        val parsed = ParsedBookingNote("  Заявка на тур: Камчатка · 12–22 авг ")
        assertEquals("Заявка на тур: Камчатка · 12–22 авг", parsed.summary)
        assertTrue(parsed.companions.isEmpty())
    }

    @Test fun oldNotesWithLongDashRulesStillParse() {
        val note = "Заявка\n👥 Кто едет (2)\n————\n1. Хличкин Вячеслав\nДР: 03.01.1987\n————\n2. Козикова Анна\nЗагран: 1"
        val parsed = ParsedBookingNote(note)
        assertEquals(listOf("Хличкин Вячеслав", "Козикова Анна"), parsed.companions.map { it.first })
        assertEquals(listOf("ДР: 03.01.1987"), parsed.companions[0].second)
        assertTrue(parsed.companions[1].second.none { it.startsWith("2.") })
    }

    // MARK: - Booking text for the support chat (iOS supportText)

    @Test fun supportTextListsDetailsPeopleAndContact() {
        val r = BookingRequest("Алтай", "1–8 мая 2027", "Слава", "+79990000000", peopleCount = 2,
            details = listOf("Тур: Алтай", "Заезд: 1–8 мая 2027"))
        assertEquals(
            "📝 Заявка на тур\n\nТур: Алтай\nЗаезд: 1–8 мая 2027\nЧеловек: 2\nКонтакт: Слава\nТел: +7 999 000-00-00",
            supportText(r),
        )
        val bare = BookingRequest("Алтай", "1–8 мая 2027", "Слава", "+79990000000", showsPeople = false)
        assertEquals("📝 Заявка на тур\n\nАлтай\n1–8 мая 2027\nКонтакт: Слава\nТел: +7 999 000-00-00", supportText(bare))
        val withPeople = r.copy(companions = listOf(TravelCompanion(lastName = "Петрова", firstName = "Анна")))
        assertTrue(supportText(withPeople).endsWith("\n\n👥 Кто едет (1)\n--------\n1. Петрова Анна"))
    }
}
