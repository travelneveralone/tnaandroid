package com.travelneveralone.tna

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class CatalogFilter(val title: String) {
    All("Все"),
    Russia("Россия"),
    World("Мир"),
    Winter("Зима"),
    Spring("Весна"),
    Summer("Лето"),
    Autumn("Осень");

    val region: TourRegion?
        get() = when (this) {
            Russia -> TourRegion.Russia
            World -> TourRegion.World
            else -> null
        }

    val season: String?
        get() = when (this) {
            Winter -> "Зима"
            Spring -> "Весна"
            Summer -> "Лето"
            Autumn -> "Осень"
            else -> null
        }

    fun apply(tours: List<Tour>): List<Tour> {
        region?.let { region -> return tours.filter { it.region == region } }
        season?.let { season -> return tours.filter { it.seasons.contains(season) } }
        return tours
    }

    fun narrows(tours: List<Tour>): Boolean {
        if (this == All) return true
        val count = apply(tours).size
        return count > 0 && count < tours.size
    }

    companion object {
        fun available(tours: List<Tour>): List<CatalogFilter> = entries.filter { it.narrows(tours) }
    }
}

fun filterTours(tours: List<Tour>, filter: CatalogFilter, search: String): List<Tour> {
    val byFilter = filter.apply(tours)
    val query = search.trim()
    if (query.isEmpty()) return byFilter
    return byFilter.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.tagline.contains(query, ignoreCase = true) ||
            it.seasonText.contains(query, ignoreCase = true)
    }
}

enum class QuizVibe(val title: String, val emoji: String) {
    Mountains("Горы и вулканы", "🏔"),
    Ocean("Океан и пляжи", "🌊"),
    Ice("Лёд и север", "❄️"),
    Exotic("Экзотика", "🐉"),
}

enum class QuizSeason(val title: String) {
    Winter("Зима"), Spring("Весна"), Summer("Лето"), Autumn("Осень"), Any("Когда угодно"),
}

enum class QuizEffort(val title: String, val range: IntRange) {
    Easy("Полегче", 1..2),
    Medium("Средне", 2..3),
    Hard("Хардкор", 4..5),
}

enum class QuizBudget(val title: String, val limit: Int?) {
    Low("до 100 000 ₽", 100_000),
    Mid("до 250 000 ₽", 250_000),
    Any("Не важно", null),
}

data class QuizAnswers(
    val vibe: QuizVibe? = null,
    val season: QuizSeason? = null,
    val effort: QuizEffort? = null,
    val budget: QuizBudget? = null,
)

data class TourMatch(val tour: Tour, val percent: Int) {
    val id: UUID get() = tour.id
}

object TourMatchEngine {
    private val vibesBySymbol: Map<String, Set<QuizVibe>> = mapOf(
        "mountain.2.fill" to setOf(QuizVibe.Mountains),
        "leaf.fill" to setOf(QuizVibe.Mountains),
        "building.columns.fill" to setOf(QuizVibe.Mountains, QuizVibe.Exotic),
        "snowflake" to setOf(QuizVibe.Ice),
        "sparkles" to setOf(QuizVibe.Ice),
        "water.waves" to setOf(QuizVibe.Ocean, QuizVibe.Ice),
        "tree.fill" to setOf(QuizVibe.Ocean, QuizVibe.Exotic),
        "camera.macro" to setOf(QuizVibe.Exotic),
        "pawprint.fill" to setOf(QuizVibe.Exotic, QuizVibe.Ocean),
    )

    fun rank(tours: List<Tour>, answers: QuizAnswers): List<TourMatch> {
        val scored = tours.map { it to score(it, answers) }
        val maxScore = maxOf(scored.maxOfOrNull { it.second } ?: 1, 1)
        return scored
            .sortedByDescending { it.second }
            .map { (tour, score) -> TourMatch(tour, (score.toDouble() / maxScore * 100).toInt()) }
    }

    fun best(tours: List<Tour>, answers: QuizAnswers): TourMatch? = rank(tours, answers).firstOrNull()

    private fun score(tour: Tour, answers: QuizAnswers): Int {
        var score = 10
        answers.vibe?.let { vibe ->
            val vibes = vibesBySymbol[tour.symbol].orEmpty()
            if (vibes.contains(vibe)) score += 40
        }
        val season = answers.season
        if (season != null && season != QuizSeason.Any) {
            if (tour.seasons.contains(season.title)) score += 25
        } else {
            score += 10
        }
        answers.effort?.let { effort ->
            if (tour.difficulty in effort.range) score += 20
            else if (kotlin.math.abs(tour.difficulty - effort.range.first) <= 1 ||
                kotlin.math.abs(tour.difficulty - effort.range.last) <= 1
            ) score += 8
        }
        val limit = answers.budget?.limit
        if (limit != null) {
            if (tour.displayPrice <= limit) score += 15
        } else if (answers.budget == QuizBudget.Any) {
            score += 8
        }
        return score
    }
}

/// Booking request state (iOS `BookingViewModel`). Fields are Compose state so the sheet
/// redraws as the person types; the rules are plain and covered by DomainRulesTest.
class BookingForm(
    val tour: Tour,
    val departure: Departure?,
    name: String = "",
    phone: String = "+7 ",
) {
    var name by mutableStateOf(name)
    var phone by mutableStateOf(phone)
    var peopleCount by mutableStateOf(1)
    var datesUnknown by mutableStateOf(false)
    var startDate by mutableStateOf<java.time.LocalDate?>(null)
    var endDate by mutableStateOf<java.time.LocalDate?>(null)
    var friendCode by mutableStateOf("")
    var alreadyReferred by mutableStateOf(false)
    /** This submission bound a friend's code - the success screen says so (TNA-186). */
    var referralAccepted by mutableStateOf(false)
    var companions by mutableStateOf<List<TravelCompanion>>(emptyList())
    var isSubmitting by mutableStateOf(false)
    var isDone by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    val cert = CertificateRedemption()

    val isCustomDates: Boolean get() = departure == null
    val isWaitlist: Boolean get() = departure?.isSoldOut == true
    val hasCustomRange: Boolean
        get() {
            val start = startDate ?: return false
            val end = endDate ?: return false
            return !end.isBefore(start)
        }
    val canSubmit: Boolean
        get() = name.trim().length >= 2 &&
            phone.count { it.isDigit() } >= 10 &&
            (!isCustomDates || datesUnknown || hasCustomRange) &&
            !isSubmitting

    val maxPeople: Int get() = departure?.let { maxOf(1, it.spotsLeft) } ?: 10
    val showsReferralField: Boolean get() = !isWaitlist && !alreadyReferred
    val canAttachCompanions: Boolean get() = !isWaitlist
    val canUseCertificate: Boolean get() = !isWaitlist

    val companionSummary: String
        get() = if (companions.isEmpty()) "Добавить данные тех, для кого бронируешь" else "Выбрано: ${companions.size}"

    fun applyCompanions(chosen: List<TravelCompanion>) {
        // Drafts never ride along with a request (iOS BookingViewModel.applyCompanions).
        val chosen = chosen.filter { !it.isDraft }
        companions = chosen
        if (chosen.isNotEmpty()) peopleCount = chosen.size.coerceIn(1, maxPeople)
    }

    val seatLimitNote: String?
        get() {
            val seats = departure?.spotsLeft ?: return null
            if (seats <= 0) return "Мест на этот заезд не осталось - оставь заявку, подскажем ближайшие даты"
            return "На этот заезд осталось $seats ${pluralRU(seats, "место", "места", "мест")} - больше выбрать нельзя"
        }

    val departureText: String
        get() {
            departure?.let { return it.label }
            if (datesUnknown) return "Свои даты: по договорённости"
            val label = customDatesLabel ?: return "Свои даты"
            return "Свои даты: $label"
        }

    val customDatesLabel: String?
        get() {
            val start = startDate ?: return null
            val end = endDate ?: return null
            return rangeLabel(start, end)
        }

    /// Lines for the manager (support chat + amo), same order as iOS.
    fun details(): List<String> {
        val dates = when {
            departure != null -> "Заезд: ${departure.label}"
            datesUnknown -> "Даты: по договорённости"
            else -> "Даты (свои): ${customDatesLabel ?: "не выбраны"}"
        }
        return listOfNotNull("Тур: ${tour.name}", dates, cert.detailsLine)
    }

    fun request(): BookingRequest = BookingRequest(
        tourName = tour.name,
        departureLabel = departureText,
        name = name.trim(),
        phone = PhoneNumber.e164(phone),
        peopleCount = peopleCount.coerceIn(1, maxPeople),
        companions = companions,
        details = details(),
        departureId = departure?.backendId,
        certificateCode = cert.verifiedCode,
        certificatePin = cert.verifiedPin,
    )

    companion object {
        private val monthsShort = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

        /// «3–9 мар» or «28 фев – 5 мар».
        fun rangeLabel(start: java.time.LocalDate, end: java.time.LocalDate): String {
            val m1 = monthsShort[start.monthValue - 1]
            val m2 = monthsShort[end.monthValue - 1]
            return if (start.monthValue == end.monthValue && start.year == end.year) {
                "${start.dayOfMonth}–${end.dayOfMonth} $m2"
            } else {
                "${start.dayOfMonth} $m1 – ${end.dayOfMonth} $m2"
            }
        }
    }
}

/// Gift-certificate number + PIN typed into a request form (iOS `CertificateRedemption`).
class CertificateRedemption {
    var code by mutableStateOf(PREFIX)
    var pin by mutableStateOf("")
    var result by mutableStateOf<CertificateCheck?>(null)
    var amount by mutableStateOf(0)
    var isChecking by mutableStateOf(false)

    val applied: Boolean get() = result == CertificateCheck.Applicable
    val statusText: String?
        get() = when (val r = result) {
            null -> null
            CertificateCheck.Applicable -> "Сертификат на ${Money.grouped(amount)} ₽ применён ✓"
            else -> r.userMessage
        }
    val codeComplete: Boolean get() = code.count { it.isLetterOrDigit() } >= 3 + BODY_LENGTH
    val canCheck: Boolean get() = codeComplete && pin.trim().length == 4 && !isChecking
    /// The checked pair, sent with the request (attach_certificate, TNACloud 0190).
    /// Editing either field clears `result`, so `applied` means this exact pair.
    val verifiedCode: String? get() = if (applied) code.trim().uppercase() else null
    val verifiedPin: String? get() = if (applied) pin.trim() else null
    val detailsLine: String?
        get() = if (!applied) null
        else "Сертификат: ${code.trim().uppercase()} на ${Money.grouped(amount)} ₽ (проверен, PIN сошёлся)"

    fun updateCode(raw: String) {
        code = formattedCode(raw)
        result = null
    }

    fun updatePin(raw: String) {
        pin = formattedPin(raw)
        result = null
    }

    /// The person's own certificates usable right now - one-tap picks above the fields.
    var mine by mutableStateOf<List<GiftCertificate>>(emptyList())

    fun isPicked(c: GiftCertificate): Boolean = c.code != null && code == c.code && pin == c.pin

    /// Fills number and PIN from the person's own certificate; the caller verifies it.
    fun pick(c: GiftCertificate) {
        code = formattedCode(c.code ?: "")
        pin = formattedPin(c.pin)
        result = null
    }

    companion object {
        const val PREFIX = "TNA-"
        private const val BODY_LENGTH = 8

        /// The form types the dashes and "TNA-"; the person types characters.
        fun formattedCode(raw: String): String {
            var body = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
            if (body.startsWith("TNA")) body = body.drop(3)
            else if ("TNA".startsWith(body)) body = ""
            body = body.take(BODY_LENGTH)
            val head = body.take(4)
            val tail = body.drop(4)
            return PREFIX + head + if (tail.isEmpty()) "" else "-$tail"
        }

        fun formattedPin(raw: String): String = raw.filter { it.isDigit() }.take(4)

        /// Paid and not expired - the only certificates worth offering in a request.
        fun usable(all: List<GiftCertificate>, today: java.time.LocalDate): List<GiftCertificate> =
            all.filter { c ->
                c.status == "paid" && !c.code.isNullOrBlank() &&
                    (Dates.parseDay(c.expiresOn)?.let { !it.isBefore(today) } ?: true)
            }
    }
}

/// The black price plate on a sale card (iOS `SalePlate`). Null when the tour is not cheaper.
/// No season line: the tour name already carries it.
data class SalePlate(
    val name: String,
    val daysLine: String,
    val price: Int,
    val base: Int,
    val mark: String,
    /// «ДО 27 НОЯБРЯ», printed next to the campaign pill, not inside it.
    val until: String,
    /// «ДО 27.11», fallback when the long form does not fit the card width.
    val untilShort: String,
) {
    companion object {
        private val ru = java.util.Locale("ru")
        private val months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        fun make(tour: Tour, untilIso: String?): SalePlate? {
            val price = tour.salePriceFrom ?: return null
            if (!tour.hasSale) return null
            val word = pluralRU(tour.days, "день", "дня", "дней")
            return SalePlate(
                name = tour.name.uppercase(ru),
                daysLine = "${tour.days} $word".uppercase(ru),
                price = price,
                base = tour.priceFrom,
                mark = (tour.saleLabel ?: "Акция").uppercase(ru),
                until = untilLine(untilIso),
                untilShort = untilShortLine(untilIso),
            )
        }

        /// `yyyy-mm-dd` → «ДО 30 НОЯБРЯ».
        fun untilLine(iso: String?): String {
            val date = Dates.parseDay(iso) ?: return ""
            return "ДО ${date.dayOfMonth} ${months[date.monthValue - 1]}".uppercase(ru)
        }

        /// `yyyy-mm-dd` → «ДО 30.11».
        fun untilShortLine(iso: String?): String {
            val date = Dates.parseDay(iso) ?: return ""
            return "ДО %02d.%02d".format(date.dayOfMonth, date.monthValue)
        }
    }
}
