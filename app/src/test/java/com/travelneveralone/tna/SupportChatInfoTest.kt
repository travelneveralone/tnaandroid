package com.travelneveralone.tna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** TNA-116 - iOS SupportChatInfoTests: the support status is computed from the
 *  schedule (10:00-21:00 Moscow, every day, 1 January off) and shown in the
 *  phone's time zone. */
class SupportChatInfoTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val info = SupportChatInfo.standard

    private fun msk(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(moscow).toInstant()

    @Test fun onlineDuringWorkingHours() {
        val now = msk(2026, 11, 25, 12)
        assertEquals(SupportAvailability.Online(msk(2026, 11, 25, 21)), info.availability(now))
        assertEquals("На связи до 21:00 мск", info.headerStatus(now, moscow))
        assertNull(info.offlineNote(now, moscow))
    }

    @Test fun closingMinuteIsAlreadyOffline() {
        assertEquals(SupportAvailability.Offline(msk(2026, 11, 26, 10)), info.availability(msk(2026, 11, 25, 21)))
    }

    @Test fun beforeOpeningAnswersToday() {
        assertEquals("Не на связи · ответим с 10:00 мск", info.headerStatus(msk(2026, 11, 25, 9, 30), moscow))
    }

    @Test fun eveningAnswersTomorrow() {
        val now = msk(2026, 11, 25, 22)
        assertEquals("Не на связи · ответим завтра с 10:00 мск", info.headerStatus(now, moscow))
        assertEquals("Сейчас не на связи - ответим завтра с 10:00 мск. Написать можно уже сейчас.", info.offlineNote(now, moscow))
    }

    @Test fun firstOfJanuaryIsSkipped() {
        val now = msk(2026, 12, 31, 22)
        assertEquals(SupportAvailability.Offline(msk(2027, 1, 2, 10)), info.availability(now))
        assertEquals("Не на связи · ответим 2 января с 10:00 мск", info.headerStatus(now, moscow))
        assertFalse(info.availability(msk(2027, 1, 1, 12)).isOnline)
    }

    @Test fun hoursFollowThePhoneTimeZone() {
        // Moscow is UTC+3, Amsterdam in winter UTC+1.
        val now = msk(2026, 11, 25, 12)
        assertEquals("На связи до 19:00", info.headerStatus(now, amsterdam))
        assertEquals("Ежедневно с 08:00 до 19:00 по твоему времени", info.hoursLine(now, amsterdam))
        assertEquals("Ежедневно с 10:00 до 21:00 мск", info.hoursLine(now, moscow))
    }

    @Test fun tomorrowIsCountedByThePhoneCalendar() {
        assertEquals("Не на связи · ответим завтра с 08:00", info.headerStatus(msk(2026, 11, 25, 23, 30), amsterdam))
    }

    @Test fun workDaysLabel() {
        assertEquals("Пн-Пт", info.copy(workDays = listOf(1, 2, 3, 4, 5)).daysLabel)
        assertEquals("Пн-Пт, Вс", info.copy(workDays = listOf(1, 2, 3, 4, 5, 7)).daysLabel)
        assertEquals("Сб, Вс", info.copy(workDays = listOf(6, 7)).daysLabel)
        assertEquals("Пн, Ср", info.copy(workDays = listOf(1, 3)).daysLabel)
    }

    @Test fun weekendIsSkipped() {
        val weekdays = info.copy(workDays = listOf(1, 2, 3, 4, 5))
        // Friday 22:00 → Monday 10:00.
        assertEquals(SupportAvailability.Offline(msk(2026, 11, 30, 10)), weekdays.availability(msk(2026, 11, 27, 22)))
    }

    @Test fun daysOffLine() {
        assertEquals("Выходной: 1 января", info.daysOffLine)
        assertEquals("Выходные: 1 января, 8 марта", info.copy(daysOff = listOf("01-01", "03-08")).daysOffLine)
        assertNull(info.copy(daysOff = emptyList()).daysOffLine)
    }

    @Test fun brokenScheduleIsNeverOnline() {
        val broken = info.copy(opensAt = "21:00", closesAt = "10:00")
        assertEquals(SupportAvailability.Offline(null), broken.availability(msk(2026, 11, 25, 12)))
        assertEquals("Сейчас не на связи", broken.headerStatus(msk(2026, 11, 25, 12), moscow))
    }

    @Test fun parsesBackendAnswer() {
        val got = SupportChatInfo.parse("""
            {"title": "Мы на связи", "time_zone": "Europe/Moscow", "opens_at": "09:00",
             "closes_at": "18:00", "work_days": [1,2,3,4,5], "days_off": [],
             "greeting_title": "Привет", "greeting_text": "Текст", "hints": ["Вопрос"],
             "team_photo_url": "https://example.com/team.jpg"}
        """.trimIndent())
        assertEquals("Мы на связи", got.title)
        assertEquals("09:00", got.opensAt)
        assertEquals(listOf(1, 2, 3, 4, 5), got.workDays)
        assertEquals(emptyList<String>(), got.daysOff)
        assertEquals(listOf("Вопрос"), got.hints)
        assertEquals("https://example.com/team.jpg", got.teamPhotoUrl)
    }

    @Test fun emptyAnswerKeepsStandard() {
        assertEquals(SupportChatInfo.standard, SupportChatInfo.parse("""{"title": "  ", "team_photo_url": null}"""))
        assertEquals(SupportChatInfo.standard, SupportChatInfo.parse(null))
        assertEquals(SupportChatInfo.standard, SupportChatInfo.parse("не json"))
    }
}
