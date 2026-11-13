package com.travelneveralone.tna

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

class BookingForm(
    val tour: Tour,
    val departure: Departure?,
    name: String = "",
    phone: String = "+7 ",
) {
    var name: String = name
    var phone: String = phone
    var peopleCount: Int = 1
    var datesUnknown: Boolean = false
    var startDate: java.time.LocalDate? = null
    var endDate: java.time.LocalDate? = null
    var friendCode: String = ""
    var isSubmitting: Boolean = false

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

    val seatLimitNote: String?
        get() {
            val seats = departure?.spotsLeft ?: return null
            if (seats <= 0) return "Мест на этот заезд не осталось — оставь заявку, подскажем ближайшие даты"
            return "На этот заезд осталось $seats ${pluralRU(seats, "место", "места", "мест")} — больше выбрать нельзя"
        }

    val departureText: String
        get() {
            departure?.let { return it.label }
            if (datesUnknown) return "Свои даты: по договорённости"
            val start = startDate
            val end = endDate
            if (start != null && end != null && !end.isBefore(start)) {
                return "Свои даты: ${start.dayOfMonth}–${end.dayOfMonth}"
            }
            return "Свои даты"
        }

    fun request(): BookingRequest = BookingRequest(
        tourName = tour.name,
        departureLabel = departureText,
        name = name.trim(),
        phone = PhoneNumber.e164(phone),
        peopleCount = peopleCount,
        departureId = departure?.backendId,
    )
}
