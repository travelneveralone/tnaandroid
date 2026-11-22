package com.travelneveralone.tna

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The booking form and the gift-certificate fields (iOS BookingViewModelTests,
 * GiftCertificateTests). Everything here is what decides whether a request can
 * be sent and what the manager reads in it.
 */
class BookingRulesTest {
    private val tour = Mock.tours.first()

    private fun form(departure: Departure? = Departure(label = "1–8 мая 2027", spotsLeft = 5, backendId = UUID.randomUUID())) =
        BookingForm(tour, departure, name = "Слава", phone = "+7 999 000-00-00")

    // MARK: - Can the request go

    @Test fun fixedDepartureNeedsOnlyNameAndPhone() {
        val f = form()
        assertTrue(f.canSubmit)
        f.name = " С "
        assertFalse("one-letter name", f.canSubmit)
        f.name = "Слава"
        f.phone = "+7 999"
        assertFalse("short phone", f.canSubmit)
        f.phone = "+7 999 000-00-00"
        f.isSubmitting = true
        assertFalse("double tap", f.canSubmit)
    }

    @Test fun customDatesNeedARangeOrTheUnknownTick() {
        val f = form(departure = null)
        assertTrue(f.isCustomDates)
        assertFalse(f.canSubmit)
        f.startDate = LocalDate.of(2027, 5, 10)
        assertFalse("only the start", f.canSubmit)
        f.endDate = LocalDate.of(2027, 5, 9)
        assertFalse("end before start", f.canSubmit)
        f.endDate = LocalDate.of(2027, 5, 10)
        assertTrue("one-day range", f.canSubmit)
        f.startDate = null
        f.datesUnknown = true
        assertTrue(f.canSubmit)
    }

    @Test fun soldOutDepartureIsAWaitlistWithoutExtras() {
        val f = form(Departure(label = "x", spotsLeft = 0))
        assertTrue(f.isWaitlist)
        assertFalse(f.showsReferralField)
        assertFalse(f.canAttachCompanions)
        assertFalse(f.canUseCertificate)
        assertEquals(1, f.maxPeople)
        assertTrue(f.seatLimitNote!!.startsWith("Мест на этот заезд не осталось"))
    }

    @Test fun referralFieldHiddenOnceACodeIsAttached() {
        val f = form()
        assertTrue(f.showsReferralField)
        f.alreadyReferred = true
        assertFalse(f.showsReferralField)
    }

    @Test fun seatsCapThePeopleCount() {
        val f = form(Departure(label = "x", spotsLeft = 2))
        assertEquals(2, f.maxPeople)
        assertEquals("На этот заезд осталось 2 места - больше выбрать нельзя", f.seatLimitNote)
        assertEquals("На этот заезд осталось 1 место - больше выбрать нельзя", form(Departure(label = "x", spotsLeft = 1)).seatLimitNote)
        assertEquals(10, form(departure = null).maxPeople)
        assertNull(form(departure = null).seatLimitNote)
    }

    @Test fun companionsSetThePeopleCountWithinTheSeats() {
        val f = form(Departure(label = "x", spotsLeft = 2))
        assertEquals("Добавить данные тех, для кого бронируешь", f.companionSummary)
        val three = List(3) { TravelCompanion(lastName = "Л$it", firstName = "И$it") }
        f.applyCompanions(three)
        assertEquals(2, f.peopleCount)
        assertEquals("Выбрано: 3", f.companionSummary)
        f.peopleCount = 2
        f.applyCompanions(emptyList())
        assertEquals("cleared list keeps the count", 2, f.peopleCount)
    }

    // MARK: - What the manager reads

    @Test fun customRangeLabelUsesGenitiveMonthsLikeDepartureDates() {
        assertEquals("3–9 мар", BookingForm.rangeLabel(LocalDate.of(2027, 3, 3), LocalDate.of(2027, 3, 9)))
        assertEquals("28 фев – 5 мар", BookingForm.rangeLabel(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 5)))
        assertEquals("1–8 мая", BookingForm.rangeLabel(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 8)))
        assertEquals("30 апр – 2 мая", BookingForm.rangeLabel(LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 2)))
        assertEquals("30 дек – 2 янв", BookingForm.rangeLabel(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)))
        assertEquals("20 янв – 2 янв", BookingForm.rangeLabel(LocalDate.of(2027, 1, 20), LocalDate.of(2028, 1, 2)))
    }

    @Test fun departureTextForEachDateMode() {
        assertEquals("1–8 мая 2027", form().departureText)
        val f = form(departure = null)
        assertEquals("Свои даты", f.departureText)
        f.startDate = LocalDate.of(2027, 9, 12)
        f.endDate = LocalDate.of(2027, 9, 22)
        assertEquals("Свои даты: 12–22 сен", f.departureText)
        f.datesUnknown = true
        assertEquals("Свои даты: по договорённости", f.departureText)
    }

    @Test fun detailsListTourThenDatesThenCertificate() {
        assertEquals(listOf("Тур: ${tour.name}", "Заезд: 1–8 мая 2027"), form().details())
        val f = form(departure = null)
        assertEquals(listOf("Тур: ${tour.name}", "Даты (свои): не выбраны"), f.details())
        f.datesUnknown = true
        assertEquals("Даты: по договорённости", f.details()[1])
        f.cert.updateCode("ABCD1234")
        f.cert.amount = 50_000
        f.cert.result = CertificateCheck.Applicable
        assertEquals("Сертификат: TNA-ABCD-1234 на 50 000 ₽ (проверен, PIN сошёлся)", f.details()[2])
    }

    @Test fun requestNormalisesPhoneTrimsNameAndClampsPeople() {
        val dep = Departure(label = "x", spotsLeft = 3, backendId = UUID.randomUUID())
        val f = form(dep)
        f.name = "  Слава  "
        f.phone = "8 (999) 000-00-00"
        f.peopleCount = 7
        val r = f.request()
        assertEquals("Слава", r.name)
        assertEquals("+79990000000", r.phone)
        assertEquals(3, r.peopleCount)
        assertEquals(dep.backendId, r.departureId)
        assertEquals(tour.name, r.tourName)
        f.peopleCount = 0
        assertEquals(1, f.request().peopleCount)
    }

    // MARK: - Gift certificate fields (iOS CertificateRedemption)

    @Test fun certificateCodeIsTypedWithoutPrefixAndDashes() {
        assertEquals("TNA-", CertificateRedemption.formattedCode(""))
        assertEquals("TNA-", CertificateRedemption.formattedCode("tn"))
        assertEquals("TNA-AB", CertificateRedemption.formattedCode("ab"))
        assertEquals("TNA-ABCD-12", CertificateRedemption.formattedCode("abcd12"))
        assertEquals("TNA-ABCD-1234", CertificateRedemption.formattedCode("TNA-ABCD-1234"))
        assertEquals("TNA-ABCD-1234", CertificateRedemption.formattedCode("tna abcd 1234 99"))
        assertEquals("TNA-ABCD-1234", CertificateRedemption.formattedCode("ABCD-1234"))
        assertEquals("TNA-AB", CertificateRedemption.formattedCode("аб-ab"))
    }

    @Test fun pinIsFourDigits() {
        assertEquals("0421", CertificateRedemption.formattedPin("04-21-99"))
        assertEquals("", CertificateRedemption.formattedPin("abcd"))
    }

    @Test fun checkNeedsAFullCodeAndPin() {
        val c = CertificateRedemption()
        assertFalse(c.canCheck)
        c.updateCode("ABCD123")
        c.updatePin("1234")
        assertFalse("seven characters", c.canCheck)
        c.updateCode("ABCD1234")
        assertTrue(c.codeComplete)
        assertTrue(c.canCheck)
        c.isChecking = true
        assertFalse(c.canCheck)
    }

    @Test fun editingAFieldDropsTheResult() {
        val c = CertificateRedemption()
        c.updateCode("ABCD1234")
        c.updatePin("1234")
        c.amount = 30_000
        c.result = CertificateCheck.Applicable
        assertTrue(c.applied)
        assertEquals("Сертификат на 30 000 ₽ применён ✓", c.statusText)
        c.updatePin("1235")
        assertFalse(c.applied)
        assertNull(c.statusText)
        assertNull(c.detailsLine)
        c.result = CertificateCheck.Expired
        assertEquals(CertificateCheck.Expired.userMessage, c.statusText)
    }

    private fun gift(code: String?, pin: String, status: String, expires: String?) =
        GiftCertificate(UUID.randomUUID(), code, 10_000, "", "", status, pin = pin, expiresOn = expires)

    @Test fun onlyPaidUnexpiredCertificatesAreOffered() {
        val today = LocalDate.of(2026, 11, 21)
        val all = listOf(
            gift("TNA-AAAA-0001", "1111", "paid", null),
            gift("TNA-AAAA-0002", "2222", "paid", "2026-11-21"),
            gift("TNA-AAAA-0003", "3333", "paid", "2026-11-20"),
            gift("TNA-AAAA-0004", "4444", "redeemed", null),
            gift("TNA-AAAA-0005", "5555", "pending", null),
            gift(null, "6666", "paid", null),
            gift(" ", "7777", "paid", null),
        )
        assertEquals(listOf("TNA-AAAA-0001", "TNA-AAAA-0002"), CertificateRedemption.usable(all, today).map { it.code })
    }

    @Test fun pickFillsBothFieldsAndARealEditUnpicks() {
        val c = CertificateRedemption()
        val a = gift("TNA-AAAA-0001", "1111", "paid", null)
        val b = gift("TNA-AAAA-0002", "5678", "paid", null)
        c.pick(b)
        assertEquals("TNA-AAAA-0002", c.code)
        assertEquals("5678", c.pin)
        assertTrue(c.isPicked(b))
        assertFalse(c.isPicked(a))
        c.updatePin("5679")
        assertFalse(c.isPicked(b))
        assertFalse(c.isPicked(gift(null, "", "paid", null)))
    }

    // MARK: - Sale plate

    @Test fun salePlateOnlyForACheaperPriceAndReadsTheDate() {
        val plain = tour.copy(salePriceFrom = null, saleLabel = null)
        assertNull(SalePlate.make(plain, "2026-11-27"))
        assertNull(SalePlate.make(plain.copy(salePriceFrom = plain.priceFrom), "2026-11-27"))
        val plate = SalePlate.make(plain.copy(salePriceFrom = plain.priceFrom - 5_000, saleLabel = "Чёрная пятница"), "2026-11-27")!!
        assertEquals("ЧЁРНАЯ ПЯТНИЦА", plate.mark)
        assertEquals("ДО 27 НОЯБРЯ", plate.until)
        assertEquals("ДО 27.11", plate.untilShort)
        assertEquals(plain.priceFrom, plate.base)
        assertEquals("АКЦИЯ", SalePlate.make(plain.copy(salePriceFrom = 1), null)!!.mark)
        assertEquals("", SalePlate.untilLine(null))
        assertEquals("", SalePlate.untilShortLine("garbage"))
    }
}
