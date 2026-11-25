package com.travelneveralone.tna

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The support chat's name, working hours and empty-chat content (TNA-116,
// TNACloud 0192 `support_chat_info()`) - iOS SupportChatInfo.swift, keep in step.
//
// The status is computed on the device from the schedule, so it flips on time
// even offline and nobody switches it by hand. Times are shown in the phone's
// time zone: a traveller in Bali sees when the team answers in their own hours.

data class SupportChatInfo(
    val title: String,
    /** IANA zone the hours are defined in (Moscow). */
    val timeZone: String,
    /** "HH:mm" in [timeZone]. */
    val opensAt: String,
    val closesAt: String,
    /** ISO weekdays: 1 = Monday … 7 = Sunday. */
    val workDays: List<Int>,
    /** Days off every year, "MM-DD". */
    val daysOff: List<String>,
    val greetingTitle: String,
    val greetingText: String,
    /** Ready first messages; a tap drops one into the message field. */
    val hints: List<String>,
    val teamPhotoUrl: String?,
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(timeZone) }.getOrElse { ZoneId.of("Europe/Moscow") }

    fun availability(now: Instant): SupportAvailability {
        val open = minutes(opensAt)
        val close = minutes(closesAt)
        if (open == null || close == null || close <= open) return SupportAvailability.Offline(null)
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..14L) {
            val day = today.plusDays(offset)
            if (!isWorking(day)) continue
            val start = day.atStartOfDay(zone).plusMinutes(open.toLong()).toInstant()
            val end = day.atStartOfDay(zone).plusMinutes(close.toLong()).toInstant()
            if (now < start) return SupportAvailability.Offline(start)
            if (now < end) return SupportAvailability.Online(end)
        }
        return SupportAvailability.Offline(null)
    }

    /** One line under the chat name: «На связи до 21:00 мск» / «Не на связи · ответим завтра с 10:00 мск». */
    fun headerStatus(now: Instant, device: ZoneId = ZoneId.systemDefault()): String =
        when (val a = availability(now)) {
            is SupportAvailability.Online -> "На связи до ${clock(a.until, device)}${shortSuffix(now, device)}"
            is SupportAvailability.Offline -> a.opensAt?.let { "Не на связи · ответим ${whenPhrase(it, now, device)}" }
                ?: "Сейчас не на связи"
        }

    /** For the empty chat when nobody is answering. */
    fun offlineNote(now: Instant, device: ZoneId = ZoneId.systemDefault()): String? =
        when (val a = availability(now)) {
            is SupportAvailability.Online -> null
            is SupportAvailability.Offline -> a.opensAt?.let {
                "Сейчас не на связи - ответим ${whenPhrase(it, now, device)}. Написать можно уже сейчас."
            } ?: "Сейчас не на связи. Написать можно уже сейчас - ответим, как вернёмся."
        }

    /** «Ежедневно с 10:00 до 21:00 мск»; away from Moscow - in the phone's hours, «по твоему времени». */
    fun hoursLine(now: Instant, device: ZoneId = ZoneId.systemDefault()): String {
        val open = minutes(opensAt) ?: return ""
        val close = minutes(closesAt) ?: return ""
        val day = now.atZone(zone).toLocalDate().atStartOfDay(zone)
        val start = day.plusMinutes(open.toLong()).toInstant()
        val end = day.plusMinutes(close.toLong()).toInstant()
        val suffix = if (sameClock(now, device)) shortSuffix(now, device) else " по твоему времени"
        return "$daysLabel с ${clock(start, device)} до ${clock(end, device)}$suffix"
    }

    /** «Выходной: 1 января» - null when there are no days off. */
    val daysOffLine: String?
        get() {
            val names = daysOff.mapNotNull(::dayMonth)
            if (names.isEmpty()) return null
            return (if (names.size == 1) "Выходной: " else "Выходные: ") + names.joinToString(", ")
        }

    /** «Ежедневно» / «Пн-Пт» / «Пн-Пт, Вс». */
    val daysLabel: String
        get() {
            val days = workDays.filter { it in 1..7 }.toSet()
            if (days.size == 7) return "Ежедневно"
            val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val parts = mutableListOf<String>()
            var d = 1
            while (d <= 7) {
                if (d !in days) { d++; continue }
                var e = d
                while (e < 7 && (e + 1) in days) e++
                parts += when (e) {
                    d -> names[d - 1]
                    d + 1 -> "${names[d - 1]}, ${names[e - 1]}"
                    else -> "${names[d - 1]}-${names[e - 1]}"
                }
                d = e + 1
            }
            return parts.joinToString(", ")
        }

    private fun isWorking(day: LocalDate): Boolean {
        if (day.dayOfWeek.value !in workDays) return false
        val key = String.format(Locale.US, "%02d-%02d", day.monthValue, day.dayOfMonth)
        return key !in daysOff
    }

    private fun sameClock(now: Instant, device: ZoneId): Boolean =
        zone.rules.getOffset(now) == device.rules.getOffset(now)

    /** « мск» when the phone is on Moscow time, nothing otherwise (the times are the phone's own). */
    private fun shortSuffix(now: Instant, device: ZoneId): String =
        if (sameClock(now, device) && timeZone == "Europe/Moscow") " мск" else ""

    /** «с 10:00» today, «завтра с 10:00», «2 января с 10:00» - by the phone's calendar. */
    private fun whenPhrase(opens: Instant, now: Instant, device: ZoneId): String {
        val time = "с ${clock(opens, device)}${shortSuffix(now, device)}"
        val days = ChronoUnit.DAYS.between(now.atZone(device).toLocalDate(), opens.atZone(device).toLocalDate())
        return when {
            days <= 0 -> time
            days == 1L -> "завтра $time"
            else -> "${DAY_MONTH.format(opens.atZone(device))} $time"
        }
    }

    companion object {
        private val RU = Locale.forLanguageTag("ru-RU")
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", RU)

        /** What the app shows until the backend answers; mirrors the 0192 defaults. */
        val standard = SupportChatInfo(
            title = "Служба заботы TNA",
            timeZone = "Europe/Moscow",
            opensAt = "10:00",
            closesAt = "21:00",
            workDays = listOf(1, 2, 3, 4, 5, 6, 7),
            daysOff = listOf("01-01"),
            greetingTitle = "Привет! Это служба заботы TNA",
            greetingText = "Мы - команда TNA. Поможем выбрать тур, ответим про бронь и оплату, подскажем, что взять с собой. Пиши - отвечают живые люди.",
            hints = listOf(
                "Добрый день! Интересует тур, хотел(а) бы узнать подробности",
                "Добрый день! Возникли вопросы по забронированному туру",
                "Добрый день! Хотел(а) уточнить вопросы, связанные с оплатой тура",
            ),
            teamPhotoUrl = null,
        )

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The answer of `support_chat_info()`. A missing or blank field keeps the
         *  standard value, so `{}` (no settings row) still gives a working chat. */
        fun parse(raw: String?): SupportChatInfo {
            val obj = runCatching { JSON.parseToJsonElement(raw.orEmpty()) as? JsonObject }.getOrNull() ?: return standard
            val d = standard
            fun text(key: String, fallback: String): String =
                runCatching { obj[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
            fun strings(key: String): List<String>? = (obj[key] as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            fun ints(key: String): List<Int>? = (obj[key] as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            return SupportChatInfo(
                title = text("title", d.title),
                timeZone = text("time_zone", d.timeZone),
                opensAt = text("opens_at", d.opensAt),
                closesAt = text("closes_at", d.closesAt),
                workDays = ints("work_days") ?: d.workDays,
                daysOff = strings("days_off") ?: d.daysOff,
                greetingTitle = text("greeting_title", d.greetingTitle),
                greetingText = text("greeting_text", d.greetingText),
                hints = strings("hints") ?: d.hints,
                teamPhotoUrl = text("team_photo_url", "").ifEmpty { null },
            )
        }

        fun minutes(hhmm: String): Int? {
            val parts = hhmm.split(":")
            if (parts.size < 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..24 || m !in 0..59) return null
            return h * 60 + m
        }

        fun clock(instant: Instant, zone: ZoneId): String = CLOCK.format(instant.atZone(zone))

        /** "01-01" → «1 января». */
        fun dayMonth(mmdd: String): String? {
            val p = mmdd.split("-")
            if (p.size != 2) return null
            val m = p[0].toIntOrNull() ?: return null
            val d = p[1].toIntOrNull() ?: return null
            val md = runCatching { MonthDay.of(m, d) }.getOrNull() ?: return null
            // A leap year, so 29 February is a valid day off too.
            return DAY_MONTH.format(md.atYear(2000))
        }
    }
}

/** Is someone answering right now, and until / from when. */
sealed interface SupportAvailability {
    data class Online(val until: Instant) : SupportAvailability
    /** null - the schedule has no working hours in the next two weeks. */
    data class Offline(val opensAt: Instant?) : SupportAvailability

    val isOnline: Boolean get() = this is Online
}
